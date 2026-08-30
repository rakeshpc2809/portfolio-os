export function formatINR(val, round = true) {
  const num = round ? Math.round(parseFloat(val) || 0) : parseFloat(val) || 0;
  return `₹ ${num.toLocaleString("en-IN")}`;
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
