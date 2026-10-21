import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]


def command(*arguments):
    return subprocess.check_output(arguments, text=True, stderr=subprocess.STDOUT).strip()


def wait_for(check, timeout=90):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except Exception as error:
            last_error = error
        time.sleep(0.3)
    raise AssertionError(f"Timed out: {last_error}")


def free_port():
    with socket.socket() as connection:
        connection.bind(("127.0.0.1", 0))
        return connection.getsockname()[1]


def request(url, body=None, key=None, authorization=None):
    headers = {}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if key:
        headers["Idempotency-Key"] = key
    if authorization:
        headers["Authorization"] = authorization
    payload = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(urllib.request.Request(url, data=payload, headers=headers), timeout=8) as response:
        return json.load(response)


def main():
    for executable in ("docker", "java"):
        if not shutil.which(executable):
            raise RuntimeError(f"Missing executable: {executable}")
    jars = {
        service: ROOT / "services" / f"{service}-service" / "target" / f"{service}-service-0.0.1-SNAPSHOT.jar"
        for service in ("inventory", "order", "payment")
    }
    for jar in jars.values():
        if not jar.exists():
            raise RuntimeError(f"Build first: mvn -DskipTests package; missing {jar}")
    suffix = uuid.uuid4().hex[:10]
    postgres = f"reserveflow-smoke-pg-{suffix}"
    rabbit = f"reserveflow-smoke-rabbit-{suffix}"
    password = secrets.token_hex(24)
    logs = Path(tempfile.mkdtemp(prefix="reserveflow-flow-smoke-"))
    containers = []
    processes = []
    handles = []
    results = []
    try:
        for name, image, ports, settings in (
            (postgres, "postgres:17", ["5432"], {"POSTGRES_PASSWORD": password}),
            (rabbit, "rabbitmq:4.2.9-management", ["5672", "15672"], {
                "RABBITMQ_DEFAULT_USER": "smoke",
                "RABBITMQ_DEFAULT_PASS": password,
                "RABBITMQ_DEFAULT_VHOST": "reserveflow",
            }),
        ):
            arguments = ["docker", "run", "-d", "--name", name]
            for port in ports:
                arguments.extend(["-p", f"127.0.0.1::{port}"])
            for setting, value in settings.items():
                arguments.extend(["-e", f"{setting}={value}"])
            command(*arguments, image)
            containers.append(name)
        wait_for(lambda: command("docker", "exec", postgres, "pg_isready", "-U", "postgres"))
        pg_port = command("docker", "port", postgres, "5432").rsplit(":", 1)[1]
        rabbit_port = command("docker", "port", rabbit, "5672").rsplit(":", 1)[1]
        management_port = command("docker", "port", rabbit, "15672").rsplit(":", 1)[1]
        authorization = "Basic " + base64.b64encode(f"smoke:{password}".encode()).decode()
        management_url = f"http://127.0.0.1:{management_port}"
        wait_for(lambda: request(management_url + "/api/overview", authorization=authorization))
        for service in jars:
            command("docker", "exec", postgres, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1",
                    "-c", f"CREATE DATABASE {service}_db")
        ports = {}
        while len(set(ports.values())) != 3:
            ports = {service: free_port() for service in jars}
        urls = {service: f"http://127.0.0.1:{port}" for service, port in ports.items()}
        for service in ("inventory", "payment", "order"):
            environment = os.environ.copy()
            environment.update({
                "SERVER_PORT": str(ports[service]),
                "SERVER_ADDRESS": "127.0.0.1",
                "DB_URL": f"jdbc:postgresql://127.0.0.1:{pg_port}/{service}_db",
                "DB_USERNAME": "postgres",
                "DB_PASSWORD": password,
                "RABBITMQ_HOST": "127.0.0.1",
                "RABBITMQ_USERNAME": "smoke",
                "RABBITMQ_PASSWORD": password,
                "SPRING_RABBITMQ_PORT": rabbit_port,
                "INVENTORY_BASE_URL": urls["inventory"],
                "MESSAGING_LAB_ENABLED": "false",
                "PAYMENT_SCHEDULING_ENABLED": "true",
            })
            handle = (logs / f"{service}.log").open("w")
            handles.append(handle)
            process = subprocess.Popen(["java", "-Xmx256m", "-jar", str(jars[service])],
                                       env=environment, cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT)
            processes.append(process)

            def ready():
                if process.poll() is not None:
                    raise RuntimeError(f"{service} exited; inspect {logs}")
                try:
                    urllib.request.urlopen(urls[service] + "/", timeout=2).close()
                    return True
                except urllib.error.HTTPError as error:
                    return error.code == 404

            wait_for(ready)
            if service == "inventory":
                wait_for(lambda: request(management_url + "/api/queues/reserveflow/order.stock-reserved.learning",
                                         authorization=authorization))
        time.sleep(1)
        for scenario in ("SUCCESS", "DECLINE", "DELAY"):
            product = request(urls["inventory"] + "/api/v1/products", {
                "sku": f"SMOKE-{scenario}-{suffix}", "name": "Isolated flow smoke",
                "unitPrice": 1500, "currency": "TRY", "initialStock": 10,
            })
            body = {"productId": product["id"], "quantity": 3, "paymentScenario": scenario}
            key = f"smoke-{scenario}-{suffix}"
            started = time.monotonic()
            first = request(urls["order"] + "/api/v1/orders", body, key)
            expected = "CANCELLED" if scenario == "DECLINE" else "CONFIRMED"

            def completed():
                current = request(urls["order"] + "/api/v1/orders", body, key)
                if current["orderId"] != first["orderId"]:
                    raise AssertionError("Idempotent retry changed order ID")
                return current if current["status"] == expected else None

            final = wait_for(completed, timeout=60)
            assert final["reservationId"] and final["paymentId"], final
            assert final["totalAmount"] == 4500, final
            if scenario == "DECLINE":
                assert final["failureReason"], final
            if scenario == "DELAY":
                assert time.monotonic() - started >= 8, "Delayed payment completed too early"
            stock = request(urls["inventory"] + "/api/v1/products/" + product["id"])
            expected_on_hand = 10 if scenario == "DECLINE" else 7
            assert stock["onHand"] == expected_on_hand and stock["reserved"] == 0, stock
            replay = request(urls["order"] + "/api/v1/orders", body, key)
            assert replay["orderId"] == final["orderId"] and replay["status"] == expected, replay
            time.sleep(0.5)
            repeated_stock = request(urls["inventory"] + "/api/v1/products/" + product["id"])
            assert repeated_stock == stock, (repeated_stock, stock)
            result = {
                "scenario": scenario, "status": final["status"], "orderId": final["orderId"],
                "onHand": stock["onHand"], "reserved": stock["reserved"],
                "available": stock["available"],
            }
            results.append(result)
            print(json.dumps(result), flush=True)
        (logs / "results.json").write_text(json.dumps(results, indent=2))
        print(f"PASS: three real HTTP/RabbitMQ/PostgreSQL flows. Logs: {logs}", flush=True)
    finally:
        for process in processes:
            process.terminate()
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        for handle in handles:
            handle.close()
        for container in reversed(containers):
            subprocess.run(["docker", "rm", "-fv", container], capture_output=True, timeout=30)
        print(f"Test artifacts: {logs}; only isolated test containers were removed.", flush=True)


if __name__ == "__main__":
    main()
