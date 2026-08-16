import subprocess
import time
import os
from PIL import Image

brain_dir = '/home/rakeshpc/.gemini/antigravity/brain/9e3415e7-14a0-4cff-8405-ef29cd6b790c'
dashboard_path = '/home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt'

with open(dashboard_path, 'r') as f:
    orig_dashboard = f.read()

states = [
    ('state1', 'empty_tab0_holdings', 0, False, 'holdings_empty'),
    ('state2', 'empty_tab0_radar', 0, True, 'radar_empty'),
    ('state3', 'empty_tab1_buckets', 1, False, 'buckets_empty'),
    ('state4', 'empty_tab2_taxlots', 2, False, 'taxlots_empty'),
    ('state5', 'empty_tab3_core_node_sync_required', 3, False, 'tab3_null'),
    ('state6', 'empty_tab3_balanced', 3, False, 'tab3_balanced'),
    ('state7', 'empty_tab3_cooldown_deferred', 3, False, 'tab3_cooldown')
]

for sid, sname, page, scroll, mode in states:
    print(f'=== Capturing {sname} (Page {page}, Mode {mode}) ===')
    
    mod_code = orig_dashboard
    mod_code = mod_code.replace('if (isInitialLoading && snapshot == null)', 'if (false)')

    if mode == 'holdings_empty':
        mod_code = mod_code.replace('val holdings = snapshot?.holdings.orEmpty()', 'val holdings = emptyList<com.portfolioos.mobile.model.FlatHoldingDto>()')
        mod_code = mod_code.replace('val radarSignals = snapshot?.radarSignals.orEmpty()', 'val radarSignals = emptyList<com.portfolioos.mobile.model.PortfolioSignalDto>()')
    elif mode == 'radar_empty':
        mod_code = mod_code.replace('val radarSignals = snapshot?.radarSignals.orEmpty()', 'val radarSignals = emptyList<com.portfolioos.mobile.model.PortfolioSignalDto>()')
    elif mode == 'buckets_empty':
        mod_code = mod_code.replace('1 -> OverlapConcentrationPlaceholderView(holdings)', '1 -> OverlapConcentrationPlaceholderView(emptyList())')
    elif mode == 'taxlots_empty':
        mod_code = mod_code.replace('val taxLots = snapshot?.taxLots.orEmpty()', 'val taxLots = emptyList<com.portfolioos.mobile.model.FlatTaxLotDto>()')
    elif mode == 'tab3_null':
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', '3 -> RebalanceWaterfallView(null)')
    elif mode == 'tab3_balanced':
        balanced_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised.")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {balanced_code}')
    elif mode == 'tab3_cooldown':
        cooldown_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "No Rebalance Required — Bucket drift detected (EQUITY_CORE, EQUITY_SATELLITE, GOLD_SILVER, LIQUID_BUFFER) but sell rebalance is on 30-day cooldown (0 days since last sell)")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {cooldown_code}')

    with open(dashboard_path, 'w') as f:
        f.write(mod_code)

    subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)

    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'force-stop', 'com.portfolioos.mobile'])
    time.sleep(1)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'start', '-n', 'com.portfolioos.mobile/.MainActivity'])
    time.sleep(3)

    if page == 1:
        # Swipe 1 page right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 2:
        # Swipe 2 pages right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 3:
        # Swipe 3 pages right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)

    if scroll:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '500', '1600', '500', '600', '300'])
        time.sleep(1.5)

    raw_path = os.path.join(brain_dir, f'raw_{sname}.png')
    out_path = os.path.join(brain_dir, f'{sname}.png')

    with open(raw_path, 'wb') as out_f:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'exec-out', 'screencap', '-p'], stdout=out_f, check=True)

    img = Image.open(raw_path)
    img.thumbnail((600, 1333))
    img.save(out_path)
    print(f'Captured & saved {out_path}')

# Restore original code
with open(dashboard_path, 'w') as f:
    f.write(orig_dashboard)

subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
print('All 7 state captures complete cleanly!')
