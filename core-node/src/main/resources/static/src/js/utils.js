export function formatINR(val, round = true) {
  const num = round ? Math.round(parseFloat(val) || 0) : parseFloat(val) || 0;
  return `₹ ${num.toLocaleString("en-IN")}`;
}

export function shortenFundName(rawName) {
  if (!rawName) return "";
  return rawName
    .replace(/\s*-\s*Direct Plan Growth\s*\(Non Demat\)/gi, "")
    .replace(/\s*-\s*DIRECT GROWTH PLAN GROWTH OPTION\s*\(Non Demat\)/gi, "")
    .replace(/\s*Direct Plan\s*-\s*Growth/gi, "")
    .replace(/\s*-\s*Direct Plan Growth/gi, "")
    .replace(/\s*Direct Growth Plan Growth Option\s*\(Non Demat\)/gi, "")
    .replace(/\s*-\s*Direct Growth/gi, "")
    .replace(/\s*Direct Plan/gi, "")
    .replace(/\s*Index Fund/gi, "")
    .replace(/ICICI Prudential/gi, "ICICI")
    .replace(/Motilal Oswal/gi, "Motilal")
    .replace(/NIPPON INDIA/gi, "Nippon")
    .replace(/Mirae Asset/gi, "Mirae")
    .replace(/Edelweiss Nifty500 Multicap Momentum Quality 50/gi, "Edelweiss MomQual 50")
    .replace(/Invesco India/gi, "Invesco")
    .replace(/Kotak Mahindra/gi, "Kotak")
    .replace(/Parag Parikh/gi, "PPFAS")
    .replace(/\s+/g, " ")
    .trim();
}

export function showToast(message, type = "success") {
  const stack = document.getElementById("toastStack");
  if (!stack) return;

  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;
  stack.appendChild(toast);

  setTimeout(() => {
    toast.remove();
  }, 4000);
}
