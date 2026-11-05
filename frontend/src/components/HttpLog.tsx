import { useState } from "react";
import { SquareTerminal, ChevronRight } from "lucide-react";
import { time } from "../format";
import type { DemoState } from "../useDemo";
export default function HttpLog({ demo }: { demo: DemoState }) {
  const [showRequests, setShowRequests] = useState(false);
  const guide = demo.mode === "guide";
  return (
    <section className="request-log">
      <button
        className="request-log-toggle"
        onClick={() => setShowRequests((v) => !v)}
        aria-expanded={showRequests}
      >
        <SquareTerminal size={16} />
        <strong>HTTP isteklerini incele</strong>
        <span>
          {guide
            ? "Rehber modu · istek gönderilmez"
            : `${demo.entries.length} son istek`}
        </span>
        <ChevronRight size={16} className={showRequests ? "expanded" : ""} />
      </button>
      {showRequests && (
        <div className="request-table">
          {demo.entries.length ? (
            <table>
              <thead>
                <tr>
                  <th>Zaman</th>
                  <th>Metot</th>
                  <th>Servis / endpoint</th>
                  <th>Yanıt</th>
                  <th>Süre</th>
                </tr>
              </thead>
              <tbody>
                {demo.entries.map((e) => (
                  <tr key={e.id}>
                    <td>{time(e.at)}</td>
                    <td>
                      <code>{e.method}</code>
                    </td>
                    <td>
                      <code>{e.path}</code>
                    </td>
                    <td
                      className={
                        typeof e.status === "number" && e.status < 400
                          ? "http-ok"
                          : "http-error"
                      }
                    >
                      {e.status}
                    </td>
                    <td>{e.duration} ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p>
              Henüz HTTP isteği yok. Rehberli örnekte gerçek API çağrısı
              yapılmaz.
            </p>
          )}
          <p className="request-disclaimer">
            Yalnız bu tarayıcının HTTP istekleri listelenir. Servislerin
            birbirine gönderdiği RabbitMQ mesajları burada ölçülmez.
          </p>
        </div>
      )}
    </section>
  );
}
