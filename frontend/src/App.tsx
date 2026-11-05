import Composer from "./components/Composer";
import Results from "./components/Results";
import Presentation from "./components/Presentation";
import HttpLog from "./components/HttpLog";
import { useState } from "react";
import {
  Activity,
  ArrowUpRight,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleHelp,
  Clock3,
  ExternalLink,
  GitBranch,
  Layers3,
  Radio,
  Play,
  XCircle,
} from "lucide-react";
import { describeStatus, TERMINAL } from "./flow.mjs";
import { useDemo } from "./useDemo";
import FlowMap from "./components/FlowMap";
import Inspector from "./components/Inspector";
import type { ServiceId } from "./types";

export default function App() {
  const demo = useDemo();
  const [tab, setTab] = useState<"flow" | "architecture" | "guide">("flow");
  const [selectedService, setSelectedService] = useState<ServiceId>("order");
  const guide = demo.mode === "guide";
  const status = describeStatus(demo.order?.status);
  const terminal = !!demo.order && TERMINAL.has(demo.order.status);
  const activeStep = demo.order ? status.step : -1;

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      demo.setNotice("Panoya kopyalandı.");
    } catch {
      demo.setNotice(
        "Kopyalama izni verilmedi. Kimliği metinden seçerek kopyalayabilirsiniz.",
      );
    }
  }

  return (
    <>
      <a className="skip-link" href="#main">
        İçeriğe geç
      </a>
      <header className="app-header">
        <a className="brand" href="/" aria-label="ReserveFlow ana sayfa">
          <span className="brand-mark">
            <GitBranch size={23} />
          </span>
          <span>
            Reserve<span className="brand-light">Flow</span>
            <small>AKIŞ LABORATUVARI</small>
          </span>
        </a>
        <nav className="main-nav" aria-label="Ana menü">
          {[
            ["flow", "Sipariş akışı", Activity],
            ["architecture", "Mimari & kod", Layers3],
            ["guide", "Sunum rehberi", CircleHelp],
          ].map(([key, label, Icon]) => {
            const NavIcon = Icon as typeof Activity;
            return (
              <button
                key={key as string}
                className={tab === key ? "active" : ""}
                onClick={() => setTab(key as typeof tab)}
                aria-current={tab === key ? "page" : undefined}
              >
                <NavIcon size={16} />
                <span>{label as string}</span>
              </button>
            );
          })}
        </nav>
        <a
          className="github-link"
          href="https://github.com/aydog4nn/reserveflow"
          target="_blank"
          rel="noreferrer"
        >
          Kaynak kod <ArrowUpRight size={16} />
        </a>
      </header>

      <main id="main">
        <section className="page-heading">
          <div>
            <div className="breadcrumb">
              ReserveFlow <ChevronRight size={12} />{" "}
              {tab === "flow"
                ? "Sipariş laboratuvarı"
                : tab === "architecture"
                  ? "Mimari & kod"
                  : "Sunum rehberi"}
            </div>
            <h1>
              {tab === "flow"
                ? "Bir siparişin izini sür."
                : tab === "architecture"
                  ? "Sınıflardan servislere."
                  : "Akışı kendi cümlelerinle anlat."}
            </h1>
            <p>
              {tab === "flow"
                ? "Ürünü seç, senaryoyu çalıştır; stoktan ödemeye olan biteni incele."
                : tab === "architecture"
                  ? "Bir servise tıkla; sorumluluğunu, bağlantılarını ve ilgili kodu gör."
                  : "Önce nedenini anlat, ardından aynı akışı ekranda göster."}
            </p>
          </div>
          <div className="environment">
            <span
              className={`connection-dot ${guide ? "guide" : demo.connected ? "online" : ""}`}
            />
            <div>
              <strong>
                {guide
                  ? "Rehberli örnek"
                  : demo.connected
                    ? "Inventory erişilebilir"
                    : "Servis bağlantısı bekleniyor"}
              </strong>
              <small>
                {guide
                  ? "Örnek veri · API çağrısı yapılmaz"
                  : "Gerçek API · Yerel laboratuvar"}
              </small>
            </div>
          </div>
        </section>

        <div className="mode-bar">
          <div className="mode-switch" role="group" aria-label="Çalışma modu">
            <button
              aria-pressed={!guide}
              onClick={() => demo.changeMode("live")}
              disabled={demo.busy}
            >
              <Radio size={14} /> Canlı servisler
            </button>
            <button
              aria-pressed={guide}
              onClick={() => demo.changeMode("guide")}
              disabled={demo.busy}
            >
              <Play size={13} /> Rehberli örnek
            </button>
          </div>
          <p>
            {guide
              ? "Adımları elle ilerletirsiniz. Gösterilen sonuçlar sunucudaki siparişler değildir."
              : "Ekran, Order ve Inventory’den okunan gerçek verileri gösterir."}
          </p>
        </div>

        {(demo.error || demo.notice) && (
          <div
            className={`alert ${demo.error ? "error" : "notice"}`}
            role={demo.error ? "alert" : "status"}
          >
            {demo.error ? <XCircle size={18} /> : <CircleHelp size={18} />}
            <span>{demo.error || demo.notice}</span>
          </div>
        )}

        {tab === "flow" && (
          <div className="workspace">
            <Composer demo={demo} copy={copy} />

            <div className="main-workspace">
              <section className="flow-section">
                <div className="section-heading">
                  <div>
                    <span className="eyebrow">02 / SERVİSLER ARASI AKIŞ</span>
                    <h2>Birbirlerine nasıl bağlanıyorlar?</h2>
                  </div>
                  <span className="tiny-tag">
                    <span className="connection-dot online" />{" "}
                    {guide ? "Öğretici şema" : "Durum tabanlı görünüm"}
                  </span>
                </div>
                <FlowMap
                  order={demo.order}
                  selected={selectedService}
                  onSelect={setSelectedService}
                  guide={guide}
                />
                <div
                  className={`status-summary ${status.tone}`}
                  aria-live="polite"
                >
                  <span className="status-icon">
                    {terminal ? (
                      status.tone === "success" ? (
                        <CheckCircle2 size={21} />
                      ) : (
                        <XCircle size={21} />
                      )
                    ) : demo.order ? (
                      <Clock3 size={21} />
                    ) : (
                      <GitBranch size={21} />
                    )}
                  </span>
                  <div>
                    <strong>{status.title}</strong>
                    <p>{status.detail}</p>
                  </div>
                  {demo.order && <code>{demo.order.status}</code>}
                </div>
                <div className="step-strip">
                  {[
                    "Stok rezervasyonu",
                    "Ödeme",
                    demo.order?.paymentScenario === "DECLINE"
                      ? "Stoğu bırakma"
                      : "Stok kesinleştirme",
                    "Sipariş sonucu",
                  ].map((label, i) => (
                    <div
                      className={
                        activeStep === i
                          ? "active"
                          : activeStep > i
                            ? "past"
                            : ""
                      }
                      key={label}
                    >
                      <span>
                        {activeStep > i ? (
                          <Check size={12} />
                        ) : (
                          String(i + 1).padStart(2, "0")
                        )}
                      </span>
                      <span>{label}</span>
                    </div>
                  ))}
                </div>
                <p className="observation-note">
                  <CircleHelp size={13} />{" "}
                  {guide
                    ? "Bu adımlar öğretici örneğe aittir; sunucuya istek gönderilmez."
                    : "Vurgu, siparişin son durumundan türetilir. Mesaj trafiği veya servis sağlığı ölçümü değildir; hızlı ara durumlar sorgular arasında atlanabilir."}
                </p>
              </section>
              <Inspector selected={selectedService} />
            </div>

            <Results demo={demo} copy={copy} />
          </div>
        )}

        {tab === "architecture" && (
          <div className="architecture-page">
            <FlowMap
              order={demo.order}
              selected={selectedService}
              onSelect={setSelectedService}
              guide={guide}
            />
            <Inspector selected={selectedService} />
            <section className="layer-explainer">
              <span className="eyebrow">SPRING BOOT İSKELETİ</span>
              <h2>Bir HTTP isteğinin kod içindeki yolu</h2>
              <div className="layers">
                {[
                  ["Controller", "HTTP isteğini karşılar", "OrderController"],
                  ["Service", "İş kurallarını uygular", "OrderServiceImpl"],
                  [
                    "Persistence",
                    "Sipariş + tekrar kaydını yazar",
                    "OrderPersistenceService",
                  ],
                  [
                    "Repository",
                    "Veritabanı erişimini sağlar",
                    "OrderRepository",
                  ],
                ].map(([name, desc, code]) => (
                  <div key={name}>
                    <strong>{name}</strong>
                    <p>{desc}</p>
                    <code>{code}</code>
                  </div>
                ))}
              </div>
              <p className="architecture-note">
                Root pom.xml üç Maven modülünü listeler. Her servisin kendi
                Spring Boot başlangıç sınıfı ve yapılandırması vardır. Servisler
                birbirlerinin repository sınıflarını çağırmaz; HTTP veya
                RabbitMQ ile haberleşir.
              </p>
            </section>
          </div>
        )}

        {tab === "guide" && <Presentation onNavigate={setTab} />}

        <HttpLog demo={demo} />
      </main>
      <footer className="app-footer">
        <span>
          <span className="footer-mark">R</span> ReserveFlow{" "}
          <span>Backend’i görünür kılan laboratuvar.</span>
        </span>
        <span>
          Spring Boot <span>·</span> RabbitMQ <span>·</span> PostgreSQL{" "}
          <a
            href="https://github.com/aydog4nn/reserveflow"
            target="_blank"
            rel="noreferrer"
            aria-label="GitHub deposunu aç"
          >
            <ExternalLink size={13} />
          </a>
        </span>
      </footer>
    </>
  );
}
