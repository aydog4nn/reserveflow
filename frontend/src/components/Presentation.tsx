import { useState } from "react";
import { ChevronRight, Play, ArrowRight } from "lucide-react";
import { presentationSteps } from "../content";
export default function Presentation({
  onNavigate,
}: {
  onNavigate: (tab: "flow" | "architecture") => void;
}) {
  const [guideChapter, setGuideChapter] = useState(0);
  return (
    <section className="presentation">
      <aside>
        <span className="eyebrow">5 KISA ANLATIM ADIMI</span>
        <h2>Sunumu birlikte kur.</h2>
        {presentationSteps.map((step, i) => (
          <button
            className={guideChapter === i ? "selected" : ""}
            key={step.title}
            onClick={() => setGuideChapter(i)}
          >
            <span>0{i + 1}</span>
            {step.title}
            <ChevronRight size={15} />
          </button>
        ))}
      </aside>
      <article>
        <span className="chapter-number">0{guideChapter + 1}</span>
        <h2>{presentationSteps[guideChapter].title}</h2>
        <p>{presentationSteps[guideChapter].text}</p>
        <div className="presentation-cue">
          <Play size={18} />
          <div>
            <strong>Ekranda göster</strong>
            <p>
              {guideChapter === 0
                ? "Mimari & kod sekmesinde üç servisi sırayla seç."
                : guideChapter === 1
                  ? "Order kutusunu seç; HTTP ile ürün okuma ve RabbitMQ komutunu göster."
                  : guideChapter === 2
                    ? "Gecikmeli ödeme ile 3 adet sipariş ver. Ödeme beklerken fiziksel, ayrılan ve satılabilir stoğu karşılaştır."
                    : guideChapter === 3
                      ? "Başarılı ve reddedilen senaryoları ayrı ayrı çalıştır; son durumları karşılaştır."
                      : "Tamamlanmış siparişte “Aynı isteği tekrar gönder” düğmesine bas; sipariş kimliğinin değişmediğini göster."}
            </p>
          </div>
        </div>
        <button
          className="primary-button"
          onClick={() =>
            onNavigate(
              guideChapter === 0 || guideChapter === 1
                ? "architecture"
                : "flow",
            )
          }
        >
          Gösterime geç <ArrowRight size={16} />
        </button>
        <p className="honesty-note">
          Bu sürüm laboratuvar uygulamasıdır. Gerçek banka entegrasyonu,
          outbox/inbox, ileri retry/DLQ ve dağıtık tracing tamamlanmış
          özellikler değildir.
        </p>
      </article>
    </section>
  );
}
