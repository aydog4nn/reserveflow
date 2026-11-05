import { ArrowUpRight, Code2, FileCode2, Lightbulb } from "lucide-react";
import { serviceDetails } from "../content";
import type { ServiceId } from "../types";

export default function Inspector({ selected }: { selected: ServiceId }) {
  const data = serviceDetails[selected];
  return (
    <section className="inspector" aria-label={`${data.label} açıklaması`}>
      <div className="section-heading">
        <div>
          <span className="eyebrow">KODUN ARKASINDA</span>
          <h3>
            {data.label} <span>{data.role}</span>
          </h3>
        </div>
        <Code2 size={19} />
      </div>
      <div className="inspector-body">
        <div>
          <p className="why">
            <Lightbulb size={16} /> {data.why}
          </p>
          <ol className="responsibilities">
            {data.steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
        </div>
        <div className="code-panel">
          <div>
            <FileCode2 size={14} />
            <span>Sınıf / çağrı haritası</span>
            <a
              href={`https://github.com/aydog4nn/reserveflow/blob/main/${data.path}`}
              target="_blank"
              rel="noreferrer"
              aria-label={`${data.label} kaynak kodunu GitHub’da aç`}
            >
              <ArrowUpRight size={15} />
            </a>
          </div>
          <pre>{data.code}</pre>
          <span className="source-path">
            {data.path.split("/").slice(-2).join("/")}
          </span>
        </div>
      </div>
    </section>
  );
}
