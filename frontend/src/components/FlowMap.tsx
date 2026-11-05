import {
  Boxes,
  CreditCard,
  Database,
  GitBranch,
  PackageCheck,
  Rabbit,
  ShoppingBag,
} from "lucide-react";
import { describeStatus } from "../flow.mjs";
import type { Order, ServiceId } from "../types";

interface Props {
  order: Order | null;
  selected: ServiceId;
  onSelect: (value: ServiceId) => void;
  guide: boolean;
}
export default function FlowMap({ order, selected, onSelect, guide }: Props) {
  const current = order ? describeStatus(order.status).service : "";
  function node(
    id: ServiceId,
    label: string,
    caption: string,
    icon: React.ReactNode,
    database?: string,
  ) {
    return (
      <button
        className={`service-node node-${id} ${selected === id ? "selected" : ""} ${current === id ? "current" : ""}`}
        onClick={() => onSelect(id)}
        aria-pressed={selected === id}
      >
        <span className="node-heading">
          <span className={`node-icon icon-${id}`}>{icon}</span>
          <span>
            <strong>{label}</strong>
            <small>{caption}</small>
          </span>
        </span>
        {database ? (
          <span className="node-db">
            <Database size={12} /> {database}
          </span>
        ) : (
          <span className="node-db">commands · events</span>
        )}
        {current === id && (
          <span className="current-label">
            {guide ? "Örnekteki adım" : "Durumun işaret ettiği servis"}
          </span>
        )}
      </button>
    );
  }
  return (
    <div
      className="flow-map"
      aria-label="Order, Inventory ve Payment; RabbitMQ üzerinden komut ve sonuç olaylarıyla haberleşir. Order ayrıca Inventory’den HTTP ile ürün okur."
    >
      <div className="diagram-topline">
        <span>
          <GitBranch size={14} /> Orchestration tabanlı saga
        </span>
        <span className="diagram-port">3 bağımsız servis</span>
      </div>
      <div className="mobile-http">HTTP · Order → Inventory: ürün bilgisi</div>
      <svg
        className="connectors"
        viewBox="0 0 880 390"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <defs>
          <marker
            id="arrow-message"
            viewBox="0 0 10 10"
            refX="8"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
          </marker>
        </defs>
        <path
          className="http-path"
          d="M 145 163 L 145 46 Q 145 34 157 34 L 728 34 Q 740 34 740 46 L 740 92"
          markerEnd="url(#arrow-message)"
        />
        <path
          className="message-path"
          d="M 243 198 H 348"
          markerEnd="url(#arrow-message)"
          markerStart="url(#arrow-message)"
        />
        <path
          className="message-path"
          d="M 539 185 H 593 Q 610 185 610 168 V 135 Q 610 120 625 120 H 644"
          markerEnd="url(#arrow-message)"
          markerStart="url(#arrow-message)"
        />
        <path
          className="message-path"
          d="M 539 216 H 594 Q 610 216 610 231 V 282 Q 610 298 626 298 H 644"
          markerEnd="url(#arrow-message)"
          markerStart="url(#arrow-message)"
        />
        <text x="440" y="24" textAnchor="middle" className="http-label">
          HTTP · ürünün fiyatı ve aktifliği
        </text>
        <text x="293" y="177" textAnchor="middle">
          komut / olay
        </text>
      </svg>
      <div className="nodes">
        {node(
          "order",
          "Order",
          "Siparişi yönetir",
          <ShoppingBag size={20} />,
          "order_db",
        )}
        {node(
          "rabbitmq",
          "RabbitMQ",
          "Mesajları yönlendirir",
          <Rabbit size={23} />,
        )}
        {node(
          "inventory",
          "Inventory",
          "Stoğu yönetir",
          <Boxes size={21} />,
          "inventory_db",
        )}
        {node(
          "payment",
          "Payment",
          "Ödemeyi simüle eder",
          <CreditCard size={21} />,
          "payment_db",
        )}
      </div>
      <div className="diagram-bottom">
        <span>
          <span className="line-sample solid" /> HTTP
        </span>
        <span>
          <span className="line-sample dashed" /> RabbitMQ komutları ve sonuç
          olayları
        </span>
        <span>
          <PackageCheck size={14} /> Kutulara tıklayıp kodu incele
        </span>
      </div>
    </div>
  );
}
