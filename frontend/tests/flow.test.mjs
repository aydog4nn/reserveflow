import test from "node:test";
import assert from "node:assert/strict";
import {
  describeStatus,
  guideSequence,
  recordObservation,
  TERMINAL,
  expectedStock,
} from "../src/flow.mjs";

test("ret yolu stok bırakma tamamlanmadan iptal göstermez", () => {
  assert.deepEqual(guideSequence("DECLINE"), [
    "PENDING_STOCK",
    "PAYMENT_PENDING",
    "COMPENSATION_PENDING",
    "CANCELLED",
  ]);
  assert.equal(TERMINAL.has("COMPENSATION_PENDING"), false);
  assert.equal(describeStatus("COMPENSATION_PENDING").service, "inventory");
  assert.equal(expectedStock(10, 3, "DECLINE"), 10);
});
test("başarı ve gecikme aynı sonlandırma yolunu izler", () => {
  assert.deepEqual(guideSequence("SUCCESS"), guideSequence("DELAY"));
  assert.equal(expectedStock(10, 3, "SUCCESS"), 7);
  assert.equal(TERMINAL.has("STOCK_COMMIT_PENDING"), false);
});
test("gözlem günlüğü atlanan durumları uydurmaz ve tekrarları eklemez", () => {
  const start = recordObservation([], { status: "PENDING_STOCK" }, "first");
  const end = recordObservation(start, { status: "CONFIRMED" }, "second");
  assert.deepEqual(end, [
    { status: "PENDING_STOCK", at: "first" },
    { status: "CONFIRMED", at: "second" },
  ]);
  assert.equal(recordObservation(end, { status: "CONFIRMED" }), end);
});
