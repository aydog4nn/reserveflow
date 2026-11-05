import type { KeyboardEvent } from "react";
import {
  Boxes,
  RefreshCw,
  LoaderCircle,
  Plus,
  Clock3,
  Play,
  ArrowRight,
  ShieldCheck,
  Copy,
  RotateCcw,
  Package,
  Keyboard,
  Headphones,
  Mouse,
  Check,
} from "lucide-react";
import { scenarios } from "../content";
import { TERMINAL } from "../flow.mjs";
import { money } from "../format";
import type { DemoState } from "../useDemo";
import type { Product } from "../types";

function ProductIcon({ product }: { product: Product }) {
  const value = `${product.sku} ${product.name}`.toLocaleLowerCase("tr");
  return /key|klavye/.test(value) ? (
    <Keyboard />
  ) : /audio|kulak|head/.test(value) ? (
    <Headphones />
  ) : /mouse|fare/.test(value) ? (
    <Mouse />
  ) : (
    <Package />
  );
}

export default function Composer({
  demo,
  copy,
}: {
  demo: DemoState;
  copy: (value: string) => Promise<void>;
}) {
  const guide = demo.mode === "guide";
  const selection =
    demo.products.find((p) => p.id === demo.selectedId) || demo.attempt?.before;
  const locked = demo.busy || !!demo.order || !!demo.attempt;
  const terminal = !!demo.order && TERMINAL.has(demo.order.status);
  const currentScenario = scenarios.find(
    (s) => s.id === (demo.order?.paymentScenario || demo.scenario),
  )!;
  function productKeys(event: KeyboardEvent<HTMLDivElement>) {
    if (
      ![
        "ArrowRight",
        "ArrowLeft",
        "ArrowUp",
        "ArrowDown",
        "Home",
        "End",
      ].includes(event.key) ||
      locked
    )
      return;
    const buttons = Array.from(
      event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="radio"]'),
    ).filter((b) => !b.disabled);
    if (!buttons.length) return;
    event.preventDefault();
    const index = buttons.findIndex(
      (b) => b.getAttribute("aria-checked") === "true",
    );
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? buttons.length - 1
          : (index +
              (["ArrowRight", "ArrowDown"].includes(event.key) ? 1 : -1) +
              buttons.length) %
            buttons.length;
    buttons[next].click();
    buttons[next].focus();
  }
  return (
    <aside className="composer" aria-label="Sipariş oluşturma">
      <div className="section-heading">
        <div>
          <span className="eyebrow">01 / BAŞLANGIÇ</span>
          <h2>Siparişi oluştur</h2>
        </div>
        <ShoppingIcon />
      </div>
      <div className="catalog-header">
        <span>Ürün seçimi</span>
        <button
          className="icon-button"
          onClick={() => void demo.refreshProducts()}
          disabled={demo.loading || guide}
          aria-label="Ürünleri yenile"
        >
          <RefreshCw size={15} className={demo.loading ? "spin" : ""} />
        </button>
      </div>
      {demo.loading && demo.products.length === 0 ? (
        <div className="empty small">
          <LoaderCircle className="spin" />
          <p>Ürünler okunuyor…</p>
        </div>
      ) : demo.products.length === 0 ? (
        <div className="empty">
          <Boxes size={30} />
          <strong>
            {demo.connected ? "Henüz ürün yok" : "Inventory bağlantısı gerekli"}
          </strong>
          <p>
            {demo.connected
              ? "Üç örnek ürünü gerçek veritabanına ekleyerek başlayın."
              : demo.catalogError ||
                "Backend servislerini başlatın veya rehberli örneği açın."}
          </p>
          {!demo.connected && (
            <button
              className="text-button"
              onClick={() => void demo.refreshProducts()}
            >
              Bağlantıyı yeniden dene <ArrowRight size={14} />
            </button>
          )}
        </div>
      ) : (
        <div
          className="product-list"
          role="radiogroup"
          onKeyDown={productKeys}
          aria-label="Sipariş ürünü"
        >
          {demo.products.map((p) => (
            <button
              role="radio"
              aria-checked={demo.selectedId === p.id}
              key={p.id}
              disabled={locked || !p.active || p.available <= 0}
              onClick={() => demo.setSelectedId(p.id)}
              className={`product-row ${demo.selectedId === p.id ? "chosen" : ""}`}
            >
              <span className="product-image">
                <ProductIcon product={p} />
              </span>
              <span className="product-name">
                <strong>{p.name}</strong>
                <small>
                  {money(p.unitPrice, p.currency)}{" "}
                  <span>· {p.available} adet</span>
                </small>
              </span>
              <span className="radio-indicator">
                {demo.selectedId === p.id && <Check size={11} />}
              </span>
            </button>
          ))}
        </div>
      )}
      {!guide && (
        <button
          className="seed-button"
          onClick={() => void demo.seed()}
          disabled={demo.busy || !demo.connected || locked}
        >
          <Plus size={14} />{" "}
          {demo.products.length
            ? "Yeni demo ürünleri ekle"
            : "Demo ürünlerini oluştur"}
        </button>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void demo.submit();
        }}
      >
        <div className="quantity-row">
          <label htmlFor="quantity">Miktar</label>
          <div className="quantity-control">
            <button
              type="button"
              aria-label="Miktarı azalt"
              disabled={locked || demo.quantity <= 1}
              onClick={() => demo.setQuantity((q) => Math.max(1, q - 1))}
            >
              −
            </button>
            <input
              id="quantity"
              type="number"
              min="1"
              max={selection?.available || 1}
              value={demo.quantity}
              disabled={locked}
              onChange={(e) => demo.setQuantity(Number(e.target.value))}
            />
            <button
              type="button"
              aria-label="Miktarı artır"
              disabled={
                locked || !selection || demo.quantity >= selection.available
              }
              onClick={() => demo.setQuantity((q) => q + 1)}
            >
              +
            </button>
          </div>
        </div>
        <fieldset className="scenario-fieldset" disabled={locked}>
          <legend>Ödeme senaryosu</legend>
          <div className="scenario-options">
            {scenarios.map((s) => (
              <label
                key={s.id}
                className={demo.scenario === s.id ? "selected" : ""}
              >
                <input
                  type="radio"
                  name="scenario"
                  value={s.id}
                  checked={demo.scenario === s.id}
                  onChange={() => demo.setScenario(s.id)}
                />
                <span>{s.label}</span>
              </label>
            ))}
          </div>
          <p className="scenario-hint">
            <Clock3 size={13} /> {currentScenario.hint}
          </p>
        </fieldset>
        <div className="order-total">
          <span>Sipariş toplamı</span>
          <strong>
            {money(
              (selection?.unitPrice || 0) * demo.quantity,
              selection?.currency || "TRY",
            )}
          </strong>
        </div>
        {!demo.order && (
          <button
            className="primary-button"
            type="submit"
            disabled={demo.busy || !selection || (!guide && !demo.connected)}
          >
            {demo.busy ? (
              <LoaderCircle size={17} className="spin" />
            ) : (
              <Play size={15} />
            )}
            {demo.attempt
              ? "Aynı isteği tekrar gönder"
              : guide
                ? "Örneği başlat"
                : "Siparişi başlat"}
            <ArrowRight size={16} />
          </button>
        )}
        {demo.order && guide && !terminal && (
          <button
            type="button"
            className="primary-button"
            onClick={demo.advanceGuide}
          >
            Sonraki adımı göster <ArrowRight size={16} />
          </button>
        )}
        {demo.order && !guide && !terminal && (
          <button
            type="button"
            className="primary-button"
            disabled={!demo.paused}
            onClick={demo.resumePolling}
          >
            {demo.paused ? (
              <RefreshCw size={16} />
            ) : (
              <LoaderCircle size={16} className="spin" />
            )}
            {demo.paused ? "İzlemeye devam et" : "Sipariş izleniyor"}
          </button>
        )}
        {demo.order && terminal && (
          <button type="button" className="primary-button" onClick={demo.reset}>
            <Plus size={16} /> Yeni sipariş
          </button>
        )}
      </form>
      <div className="composer-footnote">
        <ShieldCheck size={14} />
        <span>
          {guide
            ? "Rehber işlemleri yalnız bu ekranda gerçekleşir."
            : "Gerçek ödeme alınmaz. Payment bir simülatördür."}
        </span>
      </div>
      {demo.attempt && (
        <div className="request-identity">
          <span>
            Idempotency-Key{" "}
            <button
              className="icon-button"
              onClick={() => void copy(demo.attempt!.key)}
              aria-label="Idempotency anahtarını kopyala"
            >
              <Copy size={12} />
            </button>
          </span>
          <code>{demo.attempt.key}</code>
          <p>Aynı istek, aynı anahtar → aynı sipariş.</p>
          {terminal && (
            <button
              className="text-button"
              disabled={demo.busy}
              onClick={() => void demo.submit()}
            >
              <RotateCcw size={13} /> Aynı isteği tekrar gönder
            </button>
          )}
          {!demo.order && (
            <button
              className="text-button"
              onClick={() => {
                if (
                  window.confirm(
                    "Önceki isteğin sonucu belirsizse yeni sipariş ikinci bir kayıt oluşturabilir. Yeni bir işlem başlatılsın mı?",
                  )
                )
                  demo.reset();
              }}
            >
              Bu isteği bırak, yeni işlem başlat
            </button>
          )}
        </div>
      )}
    </aside>
  );
}

function ShoppingIcon() {
  return (
    <span className="outline-symbol">
      <Package size={18} />
    </span>
  );
}
