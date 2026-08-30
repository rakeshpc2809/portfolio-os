export const state = {
  currentFy: "2026-27",
  charts: {
    perfChart: null,
    allocChart: null,
    categoryChart: null,
  },
};

export function setCurrentFy(fy) {
  state.currentFy = fy;
}

export function getCurrentFy() {
  return state.currentFy;
}
