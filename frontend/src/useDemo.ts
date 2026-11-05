import { useCallback, useEffect, useRef, useState } from "react";
import { api, setApiObserver } from "./api";
import { demoProducts, guideProducts } from "./content";
import { guideSequence, recordObservation, TERMINAL } from "./flow.mjs";
import type {
  ApiEntry,
  Attempt,
  Observation,
  Order,
  OrderStatus,
  Product,
  Scenario,
} from "./types";

function savedAttempt(): Attempt | null {
  try {
    const value = JSON.parse(
      sessionStorage.getItem("reserveflow-attempt") || "null",
    );
    return value?.key && value?.body?.productId && value?.before ? value : null;
  } catch {
    return null;
  }
}

export function useDemo() {
  const [mode, setModeState] = useState<"live" | "guide">(() =>
    new URLSearchParams(location.search).get("mode") === "guide"
      ? "guide"
      : "live",
  );
  const [products, setProducts] = useState<Product[]>([]);
  const [selectedId, setSelectedId] = useState(() =>
    mode === "live" ? savedAttempt()?.body.productId || "" : "",
  );
  const [quantity, setQuantity] = useState(() =>
    mode === "live" ? savedAttempt()?.body.quantity || 3 : 3,
  );
  const [scenario, setScenario] = useState<Scenario>(() =>
    mode === "live" ? savedAttempt()?.body.paymentScenario || "DELAY" : "DELAY",
  );
  const [order, setOrder] = useState<Order | null>(null);
  const [history, setHistory] = useState<Observation[]>([]);
  const [entries, setEntries] = useState<ApiEntry[]>([]);
  const [attempt, setAttempt] = useState<Attempt | null>(() =>
    mode === "live" ? savedAttempt() : null,
  );
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState("");
  const [catalogError, setCatalogError] = useState("");
  const [notice, setNotice] = useState("");
  const [paused, setPaused] = useState(false);
  const [resume, setResume] = useState(0);
  const [guideIndex, setGuideIndex] = useState(0);
  const guideBase = useRef<Product | null>(null);
  const requestLock = useRef(false);
  const currentMode = useRef(mode);
  currentMode.current = mode;

  useEffect(() => {
    setApiObserver((entry) =>
      setEntries((previous) => [entry, ...previous].slice(0, 30)),
    );
    return () => setApiObserver(() => {});
  }, []);

  const storeAttempt = (value: Attempt | null) => {
    setAttempt(value);
    try {
      if (value)
        sessionStorage.setItem("reserveflow-attempt", JSON.stringify(value));
      else sessionStorage.removeItem("reserveflow-attempt");
    } catch {
      /* Session storage is optional; request identity remains in memory. */
    }
  };
  const observe = useCallback((value: Order) => {
    setOrder(value);
    setHistory((previous) => recordObservation(previous, value));
  }, []);

  const refreshProducts = useCallback(async () => {
    if (mode === "guide") return;
    setLoading(true);
    try {
      const values = await api.products();
      if (currentMode.current !== "live") return;
      setProducts(values);
      setConnected(true);
      setCatalogError("");
      setSelectedId(
        (previous) =>
          previous ||
          values.find((p) => p.active && p.available > 0)?.id ||
          values[0]?.id ||
          "",
      );
    } catch (e) {
      if (currentMode.current === "live") {
        setConnected(false);
        setCatalogError((e as Error).message);
      }
    } finally {
      setLoading(false);
    }
  }, [mode]);

  useEffect(() => {
    if (mode !== "live" || !attempt?.orderId || order) return;
    let active = true;
    api
      .order(attempt.orderId)
      .then((value) => {
        if (active) {
          observe(value);
          setError("");
        }
      })
      .catch((failure: Error) => {
        if (active) setError(failure.message);
      });
    return () => {
      active = false;
    };
  }, [mode, attempt?.orderId, order?.orderId, observe]);

  useEffect(() => {
    if (mode === "guide") {
      setProducts(guideProducts.map((p) => ({ ...p })));
      setSelectedId(guideProducts[0].id);
    } else void refreshProducts();
  }, [mode, refreshProducts]);

  useEffect(() => {
    if (mode !== "live" || !order || TERMINAL.has(order.status) || paused)
      return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const deadline = Date.now() + 45000;
    let errors = 0;
    async function poll() {
      try {
        const value = await api.order(order!.orderId);
        if (cancelled) return;
        errors = 0;
        observe(value);
        setError("");
        if (TERMINAL.has(value.status)) {
          await refreshProducts();
          return;
        }
        if (value.status !== order!.status) void refreshProducts();
      } catch (e) {
        if (cancelled) return;
        errors++;
        setError((e as Error).message);
      }
      if (cancelled) return;
      if (errors >= 3 || Date.now() >= deadline) {
        setPaused(true);
        setNotice(
          "İzleme duraklatıldı. Sipariş iptal edilmedi; son gözlenen durum korunuyor. Broker ve servis loglarını kontrol edebilir veya izlemeyi sürdürebilirsiniz.",
        );
      } else timer = setTimeout(poll, 1250);
    }
    timer = setTimeout(poll, 1250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [
    mode,
    order?.orderId,
    order?.status,
    paused,
    resume,
    observe,
    refreshProducts,
  ]);

  async function lookup(id: string) {
    if (
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        id.trim(),
      )
    ) {
      setError("Geçerli bir sipariş UUID’si girin.");
      return;
    }
    if (requestLock.current) return;
    requestLock.current = true;
    setBusy(true);
    setError("");
    try {
      const value = await api.order(id.trim());
      if (attempt?.orderId !== value.orderId) storeAttempt(null);
      setHistory([]);
      observe(value);
      setPaused(false);
      setQuantity(value.quantity);
      setScenario(value.paymentScenario);
      setSelectedId(value.productId);
      const product = await api.product(value.productId);
      setProducts((previous) => [
        ...previous.filter((p) => p.id !== product.id),
        product,
      ]);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      requestLock.current = false;
      setBusy(false);
    }
  }

  function guideOrder(
    index: number,
    base: Product,
    count: number,
    choice: Scenario,
  ): Order {
    const status = guideSequence(choice)[index] as OrderStatus;
    const now = new Date().toISOString();
    return {
      orderId: "rehber-siparis-001",
      productId: base.id,
      quantity: count,
      unitPrice: base.unitPrice,
      totalAmount: base.unitPrice * count,
      currency: base.currency,
      paymentScenario: choice,
      status,
      reservationId: index > 0 ? "rehber-rezervasyon-001" : null,
      paymentId: index > 1 ? "rehber-odeme-001" : null,
      failureReason:
        choice === "DECLINE" && index > 1 ? "Simüle edilmiş ödeme reddi" : null,
      createdAt: order?.createdAt || now,
      updatedAt: now,
    };
  }

  async function submit() {
    if (requestLock.current) return;
    const product = products.find((p) => p.id === selectedId);
    if (!product) {
      setError("Önce bir ürün seçin.");
      return;
    }
    if (
      !attempt &&
      (!Number.isInteger(quantity) ||
        quantity < 1 ||
        quantity > product.available ||
        !product.active)
    ) {
      setError(
        "Miktar pozitif bir tam sayı olmalı ve satılabilir stoğu aşmamalı.",
      );
      return;
    }
    setError("");
    setNotice("");
    if (mode === "guide") {
      guideBase.current = { ...product };
      setGuideIndex(0);
      setHistory([]);
      observe(guideOrder(0, product, quantity, scenario));
      return;
    }
    const value = attempt || {
      key: crypto.randomUUID(),
      body: { productId: selectedId, quantity, paymentScenario: scenario },
      before: { ...product },
    };
    storeAttempt(value);
    requestLock.current = true;
    setBusy(true);
    try {
      const response = await api.createOrder(value.body, value.key);
      observe(response);
      storeAttempt({ ...value, orderId: response.orderId });
      setPaused(false);
      if (order && response.orderId === order.orderId)
        setNotice(
          "Aynı istek ve aynı anahtar tekrar gönderildi. API aynı sipariş kimliğini döndürdü.",
        );
      await refreshProducts();
    } catch (e) {
      setError(
        (e as Error).message +
          " Aynı isteği tekrar gönderirken anahtar korunur; yeni sipariş başlatmak ayrı bir işlemdir.",
      );
    } finally {
      requestLock.current = false;
      setBusy(false);
    }
  }

  function advanceGuide() {
    if (!order || !guideBase.current || guideIndex >= 3) return;
    const index = guideIndex + 1;
    setGuideIndex(index);
    const next = guideOrder(
      index,
      guideBase.current,
      order.quantity,
      order.paymentScenario,
    );
    observe(next);
    const base = guideBase.current;
    const done = index === 3;
    const onHand =
      done && order.paymentScenario !== "DECLINE"
        ? base.onHand - order.quantity
        : base.onHand;
    const reserved =
      index > 0 && !done ? base.reserved + order.quantity : base.reserved;
    setProducts((previous) =>
      previous.map((p) =>
        p.id === base.id
          ? { ...base, onHand, reserved, available: onHand - reserved }
          : p,
      ),
    );
  }

  async function seed() {
    if (requestLock.current || mode !== "live") return;
    requestLock.current = true;
    setBusy(true);
    setError("");
    let created = 0;
    try {
      const batch = crypto.randomUUID().slice(0, 8);
      for (const sample of demoProducts) {
        await api.createProduct({
          ...sample,
          sku: `DEMO-${batch}-${sample.sku}`,
        });
        created++;
      }
      setNotice(
        "3 yeni demo ürünü gerçek Inventory veritabanına eklendi. Mevcut ürünler ve stoklar sıfırlanmadı.",
      );
      await refreshProducts();
    } catch (e) {
      setError(`${created} ürün eklendi. ${(e as Error).message}`);
      await refreshProducts();
    } finally {
      requestLock.current = false;
      setBusy(false);
    }
  }

  function reset() {
    storeAttempt(null);
    setOrder(null);
    setHistory([]);
    setError("");
    setNotice("");
    setPaused(false);
    guideBase.current = null;
  }
  function changeMode(value: "live" | "guide") {
    if (value === mode || busy) return;
    if ((order && !TERMINAL.has(order.status)) || (attempt && !order)) {
      if (
        !window.confirm(
          "İzleme ekranından ayrılacaksınız. Gerçek sunucudaki sipariş iptal edilmez. Devam edilsin mi?",
        )
      )
        return;
    }
    reset();
    setProducts([]);
    setSelectedId("");
    setEntries([]);
    setModeState(value);
    const url = new URL(window.location.href);
    if (value === "guide") url.searchParams.set("mode", "guide");
    else url.searchParams.delete("mode");
    window.history.replaceState(null, "", url);
    setCatalogError("");
    setConnected(false);
  }

  return {
    mode,
    changeMode,
    products,
    selectedId,
    setSelectedId,
    quantity,
    setQuantity,
    scenario,
    setScenario,
    order,
    history,
    entries,
    attempt,
    busy,
    loading,
    connected,
    error,
    catalogError,
    notice,
    setNotice,
    paused,
    baseline: mode === "guide" ? guideBase.current : attempt?.before,
    refreshProducts,
    submit,
    seed,
    reset,
    lookup,
    advanceGuide,
    resumePolling: () => {
      setPaused(false);
      setNotice("");
      setResume((n) => n + 1);
    },
  };
}

export type DemoState = ReturnType<typeof useDemo>;
