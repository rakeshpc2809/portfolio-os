import asyncio
import unittest
try:
    from tui.portfolio_os_tui import PortfolioOSTUI, DailySummaryModal, RebalanceModal, TaxLotsModal
except ImportError:
    from portfolio_os_tui import PortfolioOSTUI, DailySummaryModal, RebalanceModal, TaxLotsModal

class TestTuiModals(unittest.IsolatedAsyncioTestCase):

    async def test_daily_summary_modal_lifecycle(self):
        app = PortfolioOSTUI()
        async with app.run_test() as pilot:
            self.assertEqual(len(app.screen_stack), 1)
            # Press 'd' to open Daily Summary
            await pilot.press('d')
            await pilot.pause()
            self.assertIsInstance(app.screen, DailySummaryModal)
            
            # Verify daily summary content exists and has text
            content = app.screen.query_one('#daily-summary-content')
            self.assertIsNotNone(content)
            
            # Press 'escape' to dismiss modal
            await pilot.press('escape')
            await pilot.pause()
            self.assertNotIsInstance(app.screen, DailySummaryModal)

    async def test_rebalance_modal_lifecycle(self):
        app = PortfolioOSTUI()
        async with app.run_test() as pilot:
            await pilot.press('p')
            await pilot.pause()
            self.assertIsInstance(app.screen, RebalanceModal)
            await pilot.press('escape')
            await pilot.pause()
            self.assertNotIsInstance(app.screen, RebalanceModal)

    async def test_tax_lots_modal_lifecycle(self):
        app = PortfolioOSTUI()
        async with app.run_test() as pilot:
            await pilot.press('t')
            await pilot.pause()
            self.assertIsInstance(app.screen, TaxLotsModal)
            await pilot.press('escape')
            await pilot.pause()
            self.assertNotIsInstance(app.screen, TaxLotsModal)

if __name__ == "__main__":
    unittest.main()
