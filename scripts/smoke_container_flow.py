import http.client
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]


def prepare_environment():
    if not os.environ.get("CI_IMAGE_TAG"):
        raise RuntimeError("CI_IMAGE_TAG gerekli.")

    environment = os.environ.copy()

    for name in (
            "CI_DB_PASSWORD",
            "CI_INVENTORY_DB_PASSWORD",
            "CI_ORDER_DB_PASSWORD",
            "CI_PAYMENT_DB_PASSWORD",
            "CI_RABBITMQ_PASSWORD",
    ):
        environment[name] = secrets.token_hex(24)

    project_name = f"reserveflow-ci-{uuid.uuid4().hex[:12]}"
    return environment, project_name


def compose(environment, project_name, *arguments):
    return subprocess.run(
        [
            "docker", "compose",
            "--env-file", "/dev/null",
            "--project-name", project_name,
            "--file", str(ROOT / "compose.ci.yaml"),
            *arguments,
        ],
        cwd=ROOT,
        env=environment,
        check=True,
        capture_output=True,
        text=True,
        timeout=600,
    )


def service_url(environment, project_name, service, container_port):
    result = compose(
        environment, project_name,
        "port", service, str(container_port),
    )
    address = result.stdout.strip()

    if not address.startswith("127.0.0.1:"):
        raise RuntimeError(f"{service}: beklenmeyen port eşlemesi.")

    return f"http://{address}"


def wait_for_api(url, timeout=120):
    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url + "/", timeout=3) as response:
                if response.status == 200:
                    return
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return
        except (urllib.error.URLError, TimeoutError,
                ConnectionError, http.client.RemoteDisconnected):
            pass

        time.sleep(1)

    raise RuntimeError(f"API zamanında hazır olmadı: {url}")


def api_request(url, body=None, idempotency_key=None):
    headers = {"Accept": "application/json"}
    payload = None

    if body is not None:
        headers["Content-Type"] = "application/json"
        payload = json.dumps(body).encode("utf-8")

    if idempotency_key is not None:
        headers["Idempotency-Key"] = idempotency_key

    request = urllib.request.Request(
        url,
        data=payload,
        headers=headers,
        method="POST" if body is not None else "GET",
    )

    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def wait_for_order(order_url, body, key, order_id, timeout=120):
    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:
        order = api_request(
            order_url + "/api/v1/orders",
            body,
            idempotency_key=key,
            )

        require(
            order["orderId"] == order_id,
            "Aynı anahtarla farklı sipariş oluştu.",
            )

        if order["status"] in ("CONFIRMED", "CANCELLED"):
            return order

        time.sleep(1)

    raise RuntimeError("Sipariş zamanında sonuçlanmadı.")


def verify_stock(product, expected_on_hand):
    require(
        product["onHand"] == expected_on_hand,
        f"Beklenmeyen onHand: {product['onHand']}",
        )
    require(product["reserved"] == 0, "Ayrılmış stok sıfırlanmadı.")
    require(
        product["available"] == expected_on_hand,
        "Kullanılabilir stok beklenen değerle uyuşmuyor.",
        )


def test_scenario(inventory_url, order_url, scenario):
    product = api_request(
        inventory_url + "/api/v1/products",
        {
            "sku": f"CI-{uuid.uuid4().hex[:12]}",
            "name": f"CI Test Klavyesi {scenario}",
            "unitPrice": 1500,
            "currency": "TRY",
            "initialStock": 10,
        },
        )

    body = {
        "productId": product["id"],
        "quantity": 3,
        "paymentScenario": scenario,
    }
    key = f"ci-{uuid.uuid4().hex}"

    first_order = api_request(
        order_url + "/api/v1/orders",
        body,
        idempotency_key=key,
        )

    completed_order = wait_for_order(
        order_url, body, key, first_order["orderId"],
    )

    expected_status = "CANCELLED" if scenario == "DECLINE" else "CONFIRMED"
    expected_on_hand = 10 if scenario == "DECLINE" else 7

    require(
        completed_order["status"] == expected_status,
        f"{scenario}: beklenen {expected_status}, "
        f"gelen {completed_order['status']}",
        )
    require(
        completed_order["reservationId"] is not None,
        "Rezervasyon kimliği eksik.",
        )
    require(
        completed_order["paymentId"] is not None,
        "Ödeme kimliği eksik.",
        )

    product_url = inventory_url + f"/api/v1/products/{product['id']}"
    verify_stock(api_request(product_url), expected_on_hand)

    replay = api_request(
        order_url + "/api/v1/orders",
        body,
        idempotency_key=key,
        )

    require(
        replay["orderId"] == first_order["orderId"]
        and replay["status"] == expected_status,
        "Terminal sipariş tekrarında sonuç değişti.",
        )
    verify_stock(api_request(product_url), expected_on_hand)

    print(
        f"PASS: {scenario} → {expected_status}; "
        f"onHand={expected_on_hand}, reserved=0",
        flush=True,
    )


def report_failure(error, environment):
    if isinstance(error, subprocess.CalledProcessError):
        detail = (error.stderr or error.stdout or "")[-6000:]
        message = f"Docker Compose başarısız: {detail}"
    elif isinstance(error, subprocess.TimeoutExpired):
        message = "Docker Compose komutu zaman aşımına uğradı."
    else:
        message = str(error)

    for name in (
            "CI_DB_PASSWORD",
            "CI_INVENTORY_DB_PASSWORD",
            "CI_ORDER_DB_PASSWORD",
            "CI_PAYMENT_DB_PASSWORD",
            "CI_RABBITMQ_PASSWORD",
    ):
        value = environment.get(name)
        if value:
            message = message.replace(value, "[REDACTED]")

    print(f"FAIL: {message}", file=sys.stderr, flush=True)


def main():
    environment, project_name = prepare_environment()
    failed = False

    try:
        compose(environment, project_name, "config", "--quiet")

        print(f"Test ortamı başlatılıyor: {project_name}", flush=True)

        compose(
            environment, project_name,
            "up", "--detach", "--no-build",
        )

        inventory_url = service_url(
            environment, project_name, "inventory", 8080,
        )
        order_url = service_url(
            environment, project_name, "order", 8081,
        )

        wait_for_api(inventory_url)
        wait_for_api(order_url)

        for scenario in ("SUCCESS", "DECLINE", "DELAY"):
            test_scenario(inventory_url, order_url, scenario)

    except Exception as error:
        failed = True
        report_failure(error, environment)

    finally:
        print("Geçici test ortamı temizleniyor.", flush=True)

        try:
            compose(
                environment, project_name,
                "down", "--volumes", "--remove-orphans",
            )
        except Exception as error:
            failed = True
            report_failure(error, environment)

    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(
            f"Test hazırlığı başarısız: {type(error).__name__}",
            file=sys.stderr,
        )
        sys.exit(1)
