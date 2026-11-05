import { useState } from "react";
import { Database, Activity, Copy, Search, ArrowRight } from "lucide-react";
import { expectedStock, describeStatus } from "../flow.mjs";
import { money, time } from "../format";
import type { DemoState } from "../useDemo";
export default function Results({
  demo,
  copy,
}: {
  demo: DemoState;
  copy: (value: string) => Promise<void>;
}) {
  const guide = demo.mode === "guide";
  const product = demo.products.find(
    (p) => p.id === (demo.order?.productId || demo.selectedId),
  );
  const [lookup, setLookup] = useState("");
  return (
    <section className="result-panel">
      <div className="section-heading">
        <div>
          <span className="eyebrow">03 / GÖZLENEN SONUÇ</span>
          <h2>Stok ve sipariş kaydı</h2>
        </div>
        <Database size={18} />
      </div>
      <div className="result-columns">
        <div className="stock-result">
          <span className="mini-label">
            {guide ? "Örnekteki stok" : "Inventory’den okunan stok"}
          </span>
          <h3>{product?.name || "Ürün bekleniyor"}</h3>
          <div className="stock-numbers">
            {[
              ["Fiziksel", product?.onHand],
              ["Ayrılan", product?.reserved],
              ["Satılabilir", product?.available],
            ].map(([label, number]) => (
              <div key={label as string}>
                <strong>{number ?? "—"}</strong>
                <span>{label}</span>
              </div>
            ))}
          </div>
          <div className="stock-equation">Satılabilir = fiziksel − ayrılan</div>
          {demo.order && demo.baseline && (
            <p className="expected-result">
              {guide ? "Örnekte" : "Başka stok hareketi yoksa bu senaryoda"}{" "}
              beklenen son stok:{" "}
              <strong>
                {expectedStock(
                  demo.baseline.onHand,
                  demo.order.quantity,
                  demo.order.paymentScenario,
                )}{" "}
                adet
              </strong>
            </p>
          )}
        </div>
        <div className="observations">
          <div className="mini-label">
            {guide ? "Rehber adımları" : "API’den gözlenen durumlar"}
            <span>{demo.history.length} kayıt</span>
          </div>
          {demo.history.length ? (
            <ol>
              {demo.history.map((entry, index) => (
                <li key={`${index}-${entry.status}`}>
                  <span
                    className={`timeline-dot ${index === demo.history.length - 1 ? "latest" : ""}`}
                  />
                  <time>{time(entry.at)}</time>
                  <span>
                    <strong>{describeStatus(entry.status).title}</strong>
                    <code>{entry.status}</code>
                  </span>
                </li>
              ))}
            </ol>
          ) : (
            <div className="timeline-empty">
              <Activity size={22} />
              <p>Bir sipariş başlatınca durumlar burada görünecek.</p>
            </div>
          )}
        </div>
        <div className="order-record">
          <span className="mini-label">Sipariş kimliği</span>
          {demo.order ? (
            <>
              <div className="id-line">
                <code>{demo.order.orderId}</code>
                <button
                  className="icon-button"
                  onClick={() => void copy(demo.order!.orderId)}
                  aria-label="Sipariş kimliğini kopyala"
                >
                  <Copy size={13} />
                </button>
              </div>
              <dl>
                <div>
                  <dt>Miktar</dt>
                  <dd>{demo.order.quantity} adet</dd>
                </div>
                <div>
                  <dt>Tutar</dt>
                  <dd>{money(demo.order.totalAmount, demo.order.currency)}</dd>
                </div>
                <div>
                  <dt>Senaryo</dt>
                  <dd>{demo.order.paymentScenario}</dd>
                </div>
                <div>
                  <dt>Rezervasyon</dt>
                  <dd title={demo.order.reservationId || ""}>
                    {demo.order.reservationId
                      ? `${demo.order.reservationId.slice(0, 12)}…`
                      : "Bekleniyor"}
                  </dd>
                </div>
                <div>
                  <dt>Ödeme</dt>
                  <dd title={demo.order.paymentId || ""}>
                    {demo.order.paymentId
                      ? `${demo.order.paymentId.slice(0, 12)}…`
                      : "Bekleniyor"}
                  </dd>
                </div>
              </dl>
              {demo.order.failureReason && (
                <p className="failure-reason">{demo.order.failureReason}</p>
              )}
            </>
          ) : (
            <p className="record-empty">Henüz sipariş oluşturulmadı.</p>
          )}
          {!guide && (
            <form
              className="lookup"
              onSubmit={(e) => {
                e.preventDefault();
                void demo.lookup(lookup);
              }}
            >
              <label htmlFor="order-lookup">Mevcut siparişi aç</label>
              <div>
                <input
                  id="order-lookup"
                  placeholder="Sipariş UUID’si"
                  value={lookup}
                  onChange={(e) => setLookup(e.target.value)}
                />
                <button
                  className="icon-button"
                  disabled={demo.busy || !lookup.trim()}
                  aria-label="Siparişi sorgula"
                >
                  <Search size={16} />
                </button>
              </div>
            </form>
          )}
          {demo.attempt?.orderId && !demo.order && (
            <button
              className="text-button"
              disabled={demo.busy}
              onClick={() => void demo.lookup(demo.attempt!.orderId!)}
            >
              Önceki siparişi izlemeye dön <ArrowRight size={13} />
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
