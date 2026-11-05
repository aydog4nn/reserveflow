"""Temporary, isolated real backend for the frontend demo. Requires Docker/Java.

Build jars first. Ctrl+C deletes only this run's containers and stops its Java
processes. No existing Compose database, container or credential is reused.
"""
import argparse
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


def command(*args):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    if result.returncode:
        raise RuntimeError(f"{args[0]} işlemi başarısız oldu (kod {result.returncode}).")
    return result.stdout.strip()


def request(url, body=None, key=None):
    headers = {"Content-Type": "application/json"} if body is not None else {}
    if key:
        headers["Idempotency-Key"] = key
    payload = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(urllib.request.Request(url, data=payload, headers=headers), timeout=8) as response:
        return json.load(response)


def wait_for(check, seconds=100):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (OSError, urllib.error.URLError, RuntimeError):
            pass
        time.sleep(0.5)
    raise RuntimeError("Hazır olma kontrolü zaman aşımına uğradı.")


def endpoint_ready(url):
    try:
        request(url)
        return True
    except urllib.error.HTTPError as error:
        return error.code == 404


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--smoke", action="store_true", help="Gerçek API ile üç senaryoyu ve idempotency tekrarını doğrula.")
    parser.add_argument("--serve", action="store_true", help="Smoke kontrolünden sonra da ortamı açık tut.")
    args = parser.parse_args()
    for binary in ("docker", "java"):
        if not shutil.which(binary):
            raise RuntimeError(f"Gerekli program bulunamadı: {binary}")
    command("docker", "info", "--format", "{{.ServerVersion}}")
    jars = {name: ROOT / "services" / f"{name}-service" / "target" / f"{name}-service-0.0.1-SNAPSHOT.jar"
            for name in ("inventory", "order", "payment")}
    for jar in jars.values():
        if not jar.is_file():
            raise RuntimeError("Önce mvn -DskipTests package ile jar dosyalarını üretin.")
    ports = {"inventory": 8080, "order": 8081, "payment": 8082}
    for port in ports.values():
        with socket.socket() as test:
            try:
                test.bind(("127.0.0.1", port))
            except OSError:
                raise RuntimeError(f"{port} portu kullanımda; mevcut servisi durdurmadan lab başlatılamaz.") from None
    suffix = uuid.uuid4().hex[:10]
    password = secrets.token_hex(24)
    logs = Path(tempfile.mkdtemp(prefix="reserveflow-frontend-lab-"))
    containers, processes, handles = [], [], []
    urls = {name: f"http://127.0.0.1:{port}" for name, port in ports.items()}
    try:
        for kind, image, port, env in (
            ("postgres", "postgres:17", "5432", {"POSTGRES_PASSWORD": password}),
            ("rabbit", "rabbitmq:4.2.9-management", "5672", {
                "RABBITMQ_DEFAULT_USER": "frontend_lab", "RABBITMQ_DEFAULT_PASS": password,
                "RABBITMQ_DEFAULT_VHOST": "reserveflow"}),
        ):
            name = f"reserveflow-frontend-{kind}-{suffix}"
            arguments = ["docker", "run", "-d", "--name", name, "--label", "reserveflow.frontend-lab=true",
                         "-p", f"127.0.0.1::{port}"]
            for key, value in env.items():
                arguments.extend(["-e", f"{key}={value}"])
            command(*arguments, image)
            containers.append(name)
        postgres, rabbit = containers
        wait_for(lambda: command("docker", "exec", postgres, "pg_isready", "-U", "postgres"))
        wait_for(lambda: command("docker", "exec", rabbit, "rabbitmq-diagnostics", "-q", "check_running") is not None)
        pg_port = command("docker", "port", postgres, "5432").rsplit(":", 1)[1]
        rabbit_port = command("docker", "port", rabbit, "5672").rsplit(":", 1)[1]
        for name in jars:
            command("docker", "exec", postgres, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1",
                    "-c", f"CREATE DATABASE {name}_db")
        for name in ("inventory", "payment", "order"):
            environment = os.environ.copy()
            environment.update({
                "SERVER_PORT": str(ports[name]), "SERVER_ADDRESS": "127.0.0.1",
                "DB_URL": f"jdbc:postgresql://127.0.0.1:{pg_port}/{name}_db",
                "DB_USERNAME": "postgres", "DB_PASSWORD": password,
                "RABBITMQ_HOST": "127.0.0.1", "SPRING_RABBITMQ_PORT": rabbit_port,
                "RABBITMQ_USERNAME": "frontend_lab", "RABBITMQ_PASSWORD": password,
                "INVENTORY_BASE_URL": urls["inventory"], "MESSAGING_LAB_ENABLED": "false",
                "PAYMENT_SCHEDULING_ENABLED": "true",
            })
            handle = (logs / f"{name}.log").open("w")
            handles.append(handle)
            processes.append(subprocess.Popen(["java", "-Xmx192m", "-jar", str(jars[name])],
                                               env=environment, stdout=handle, stderr=subprocess.STDOUT))
        wait_for(lambda: request(urls["inventory"] + "/api/v1/products") == [])
        wait_for(lambda: endpoint_ready(urls["order"] + "/api/v1/orders/" + str(uuid.uuid4())))
        wait_for(lambda: endpoint_ready(urls["payment"] + "/"))
        for sku, name, price in (("KEY", "Mekanik klavye", 1500), ("AUDIO", "Kablosuz kulaklık", 2400), ("MOUSE", "Kablosuz mouse", 850)):
            request(urls["inventory"] + "/api/v1/products", {
                "sku": f"LAB-{suffix}-{sku}", "name": name, "unitPrice": price, "currency": "TRY", "initialStock": 10,
            })
        print("Gerçek ve geçici backend hazır: Inventory :8080 · Order :8081 · Payment :8082", flush=True)
        print(f"Servis logları: {logs}", flush=True)
        print("Bu ortamın verileri geçicidir. Ctrl+C yalnız bu lab ortamını temizler.", flush=True)
        if args.smoke:
            for scenario in ("SUCCESS", "DECLINE", "DELAY"):
                product = request(urls["inventory"] + "/api/v1/products", {
                    "sku": f"SMOKE-{uuid.uuid4().hex[:10]}", "name": f"ZZ API doğrulama {scenario}",
                    "unitPrice": 1500, "currency": "TRY", "initialStock": 10,
                })
                key = str(uuid.uuid4())
                body = {"productId": product["id"], "quantity": 3, "paymentScenario": scenario}
                order = request(urls["order"] + "/api/v1/orders", body, key)
                expected = "CANCELLED" if scenario == "DECLINE" else "CONFIRMED"
                def finished():
                    current = request(urls["order"] + "/api/v1/orders/" + order["orderId"])
                    return current if current["status"] == expected else None
                final = wait_for(finished, 45)
                stock = request(urls["inventory"] + "/api/v1/products/" + product["id"])
                expected_stock = 10 if scenario == "DECLINE" else 7
                assert (stock["onHand"], stock["reserved"], stock["available"]) == (expected_stock, 0, expected_stock)
                replay = request(urls["order"] + "/api/v1/orders", body, key)
                assert replay["orderId"] == final["orderId"] and replay["status"] == expected
                after = request(urls["inventory"] + "/api/v1/products/" + product["id"])
                assert (after["onHand"], after["reserved"], after["available"]) == (expected_stock, 0, expected_stock)
                print(f"PASS {scenario}: {expected}, stok {expected_stock}/0/{expected_stock}, aynı anahtar aynı sipariş", flush=True)
            assert len(request(urls["inventory"] + "/api/v1/products?limit=100")) == 6
            print("PASS gerçek katalog ve sipariş GET endpoint'leri", flush=True)
        if not args.smoke or args.serve:
            while True:
                if any(process.poll() is not None for process in processes):
                    raise RuntimeError("Bir servis beklenmedik biçimde kapandı; lab loglarını kontrol edin.")
                time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        for process in processes:
            process.terminate()
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for handle in handles:
            handle.close()
        for name in reversed(containers):
            subprocess.run(["docker", "rm", "-f", "-v", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        print("Bu çalıştırmaya ait geçici backend temizlendi.", flush=True)


if __name__ == "__main__":
    main()
