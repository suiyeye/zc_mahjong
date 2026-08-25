<template>
	<view>
		<view class="tool-entry" :class="{ docked: isDocked, dragging, 'dock-left': dockSide === 'left', 'dock-right': dockSide === 'right' }" :style="entryStyle"
			@touchstart="startDrag" @touchmove.stop.prevent="moveDrag" @touchend="endDrag" @click="handleEntryClick">
			<text class="entry-title">胡牌\n工具</text>
		</view>

		<view v-if="visible" class="modal-mask" @click="closeCalculator">
			<view class="calculator" @click.stop>
				<view class="modal-head">
					<view>
						<text class="modal-title">{{ mahjongMode === 'sichuan' ? '川麻听胡计算' : '大众麻将听胡计算' }}</text>
						<text class="tile-count" :class="{ ready: [13, 14].includes(selectedTiles.length) }">已选 {{ selectedTiles.length }} 张</text>
					</view>
					<text class="close" @click="closeCalculator">×</text>
				</view>

				<view class="mode-switch">
					<view class="mode-button" :class="{ active: mahjongMode === 'sichuan' }" @click="switchMode('sichuan')">川麻</view>
					<view class="mode-button" :class="{ active: mahjongMode === 'standard' }" @click="switchMode('standard')">大众麻将</view>
				</view>

				<scroll-view class="content-scroll" scroll-y>
					<view class="tile-wall">
						<view v-for="group in visibleTileGroups" :key="group.name" class="wall-row" :class="{ honors: group.name === '字' }">
							<view v-for="tile in group.tiles" :key="tile.id" class="mahjong-tile selector-tile"
								:class="{ disabled: tileCount(tile.id) >= 4 || selectedTiles.length >= 14 }"
								@click="addTile(tile.id)">
								<image class="tile-image" :src="tile.image" mode="aspectFit" />
								<text v-if="tileCount(tile.id)" class="tile-badge">{{ tileCount(tile.id) }}</text>
							</view>
						</view>
					</view>

					<view v-if="mahjongMode === 'sichuan'" class="rule-note">川麻只使用万、条、筒；胡牌必须缺一门，识别平胡、对对胡、清一色、七对、龙七对、清对等牌型。</view>
					<view v-else class="rule-note standard-note">大众麻将支持万、条、筒和字牌，识别普通胡、七对、十三幺等牌型。</view>

					<view class="hand-panel">
						<view class="hand-head">
							<text class="hand-title">已选牌：{{ selectedTiles.length }}</text>
							<view class="mode-hints">
								<text :class="{ active: selectedTiles.length === 13 }">13 张查听</text>
								<text :class="{ active: selectedTiles.length === 14 }">14 张查胡/舍牌</text>
							</view>
						</view>
						<view class="selected-hand">
							<view v-for="(tileId, index) in selectedTiles" :key="`${index}-${tileId}`" class="mahjong-tile hand-tile" @click="removeTileAt(index)">
								<image class="tile-image" :src="tileImage(tileId)" mode="aspectFit" />
							</view>
							<view v-for="index in emptySlots" :key="`empty-${index}`" class="empty-slot" />
						</view>
					</view>

					<view v-if="result" class="result-panel">
						<view v-if="result.mode === 'win'" class="win-result">
							<text class="result-title">已经胡牌：{{ result.type }}</text>
						</view>

						<view v-else-if="result.mode === 'waiting'" class="wait-result">
							<text class="result-title">已听牌，共听 {{ result.waits.length }} 种</text>
							<view class="result-tiles">
								<view v-for="detail in result.waitDetails" :key="detail.tileId" class="result-tile-wrap">
									<view class="mahjong-tile result-tile"><image class="tile-image" :src="tileImage(detail.tileId)" mode="aspectFit" /></view>
									<text>{{ tileName(detail.tileId) }}</text>
									<text class="tile-type">{{ detail.type }}</text>
								</view>
							</view>
						</view>

						<view v-else-if="result.mode === 'discard'" class="discard-result">
							<text class="result-title">打出以下牌可以听牌</text>
							<view v-for="option in result.discards" :key="option.discardId" class="discard-row">
								<view class="discard-label">
									<text>打</text>
									<view class="mahjong-tile result-tile"><image class="tile-image" :src="tileImage(option.discardId)" mode="aspectFit" /></view>
								</view>
								<text class="arrow">→ 听</text>
								<view class="wait-list">
									<view v-for="detail in option.waitDetails" :key="detail.tileId" class="mini-wait">
										<view class="mahjong-tile mini-tile"><image class="tile-image" :src="tileImage(detail.tileId)" mode="aspectFit" /></view>
										<text class="mini-tile-type">{{ detail.type }}</text>
									</view>
								</view>
							</view>
						</view>

						<view v-else class="lose-result">
							<text class="result-title">{{ result.message }}</text>
						</view>
					</view>
				</scroll-view>

				<view class="actions">
					<button class="clear-button" @click="clearTiles">重置</button>
					<button class="calculate-button" :disabled="![13, 14].includes(selectedTiles.length)" @click="calculate">
						{{ selectedTiles.length === 13 ? '计算听牌' : selectedTiles.length === 14 ? '计算胡牌/舍牌' : '请选择 13 或 14 张' }}
					</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { analyzeTiles, TILE_GROUPS, tileImage, tileName } from '../utils/mahjong.js'

const visible = ref(false)
const mahjongMode = ref('sichuan')
const entrySize = 56
const entryMargin = 14
const entryPosition = ref(null)
const isDocked = ref(false)
const dockSide = ref('right')
const dragStart = ref({ touchX: 0, touchY: 0, entryX: 0, entryY: 0 })
const dragging = ref(false)
const suppressClick = ref(false)
let dockTimer = null
const entryStyle = computed(() => {
	if (!entryPosition.value) return {}
	return { left: `${entryPosition.value.x}px`, top: `${entryPosition.value.y}px`, right: 'auto', bottom: 'auto' }
})
const visibleTileGroups = computed(() => mahjongMode.value === 'sichuan'
	? TILE_GROUPS.filter(group => group.name !== '字')
	: TILE_GROUPS)
const selectedTiles = ref([])
const result = ref(null)
const emptySlots = computed(() => Math.max(0, 14 - selectedTiles.value.length))

function currentEntryPosition() {
	const systemInfo = uni.getSystemInfoSync()
	return entryPosition.value || {
		x: systemInfo.windowWidth - entrySize - entryMargin,
		y: systemInfo.windowHeight - entrySize - entryMargin
	}
}

function resetDockTimer() {
	clearTimeout(dockTimer)
	dockTimer = setTimeout(dockEntry, 5000)
}

function dockEntry(side) {
	if (visible.value || dragging.value) return
	const systemInfo = uni.getSystemInfoSync()
	const current = currentEntryPosition()
	dockSide.value = side || (current.x + entrySize / 2 < systemInfo.windowWidth / 2 ? 'left' : 'right')
	entryPosition.value = {
		x: dockSide.value === 'left' ? -entrySize + 18 : systemInfo.windowWidth - 18,
		y: current.y
	}
	isDocked.value = true
}

function shouldDockAtEdge() {
	const systemInfo = uni.getSystemInfoSync()
	const current = currentEntryPosition()
	return current.x <= entryMargin + 12 || current.x >= systemInfo.windowWidth - entrySize - entryMargin - 12
}

function expandEntry() {
	if (!isDocked.value) return
	const systemInfo = uni.getSystemInfoSync()
	const current = currentEntryPosition()
	entryPosition.value = {
		x: dockSide.value === 'left' ? entryMargin : systemInfo.windowWidth - entrySize - entryMargin,
		y: current.y
	}
	isDocked.value = false
}

function startDrag(event) {
	clearTimeout(dockTimer)
	if (isDocked.value) {
		expandEntry()
		suppressClick.value = true
		setTimeout(() => { suppressClick.value = false }, 80)
		resetDockTimer()
		return
	}
	const touch = event.touches[0]
	const current = currentEntryPosition()
	entryPosition.value = current
	dragStart.value = { touchX: touch.clientX, touchY: touch.clientY, entryX: current.x, entryY: current.y }
	dragging.value = false
}

function moveDrag(event) {
	const touch = event.touches[0]
	const deltaX = touch.clientX - dragStart.value.touchX
	const deltaY = touch.clientY - dragStart.value.touchY
	if (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4) dragging.value = true
	if (!dragging.value) return
	const systemInfo = uni.getSystemInfoSync()
	entryPosition.value = {
		x: Math.min(Math.max(entryMargin, dragStart.value.entryX + deltaX), systemInfo.windowWidth - entrySize - entryMargin),
		y: Math.min(Math.max(entryMargin, dragStart.value.entryY + deltaY), systemInfo.windowHeight - entrySize - entryMargin)
	}
}

function endDrag() {
	const wasDragging = dragging.value
	dragging.value = false
	if (wasDragging) {
		suppressClick.value = true
		setTimeout(() => { suppressClick.value = false }, 80)
		if (shouldDockAtEdge()) {
			const systemInfo = uni.getSystemInfoSync()
			dockEntry(currentEntryPosition().x < systemInfo.windowWidth / 2 ? 'left' : 'right')
			return
		}
	}
	resetDockTimer()
}

function handleEntryClick() {
	if (isDocked.value) {
		expandEntry()
		resetDockTimer()
		return
	}
	if (!suppressClick.value) openCalculator()
}

function openCalculator() {
	clearTimeout(dockTimer)
	visible.value = true
}

function closeCalculator() {
	visible.value = false
	resetDockTimer()
}

onUnmounted(() => clearTimeout(dockTimer))
resetDockTimer()

function switchMode(mode) {
	if (mahjongMode.value === mode) return
	mahjongMode.value = mode
	if (mode === 'sichuan') {
		selectedTiles.value = selectedTiles.value.filter(tileId => tileId < 27)
	}
	result.value = null
	if ([13, 14].includes(selectedTiles.value.length)) calculate()
}

function tileCount(tileId) {
	return selectedTiles.value.filter(id => id === tileId).length
}

function addTile(tileId) {
	if (selectedTiles.value.length >= 14 || tileCount(tileId) >= 4) return
	selectedTiles.value.push(tileId)
	result.value = null
	if ([13, 14].includes(selectedTiles.value.length)) calculate()
}

function removeTileAt(index) {
	selectedTiles.value.splice(index, 1)
	result.value = null
	if (selectedTiles.value.length === 13) calculate()
}

function clearTiles() {
	selectedTiles.value = []
	result.value = null
}

function calculate() {
	result.value = analyzeTiles(selectedTiles.value, mahjongMode.value)
}
</script>

<style scoped lang="scss">
.tool-entry { position: fixed; z-index: 1100; right: 28rpx; bottom: calc(32rpx + env(safe-area-inset-bottom)); width: 112rpx; height: 112rpx; display: flex; align-items: center; justify-content: center; box-sizing: border-box; border-radius: 50%; background: #1e6b55; box-shadow: 0 10rpx 30rpx rgba(23, 63, 53, .28); touch-action: none; transition: left .25s ease, top .25s ease, opacity .25s ease; }
.tool-entry.dragging { transition: none; }
.tool-entry.docked { opacity: .78; }
.tool-entry.docked .entry-title { opacity: 0; }
.entry-title { width: 72rpx; color: #fff; text-align: center; font-size: 24rpx; font-weight: 700; line-height: 1.25; transition: opacity .15s ease; }
.modal-mask { position: fixed; z-index: 1200; inset: 0; display: flex; align-items: flex-end; background: rgba(10, 30, 25, .68); }
.calculator { width: 100%; height: 94vh; min-height: 0; display: flex; flex-direction: column; padding: 26rpx 18rpx calc(18rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 28rpx 28rpx 0 0; background: #f4f1e8; }
.modal-head { flex-shrink: 0; display: flex; align-items: flex-start; justify-content: space-between; padding: 0 8rpx 16rpx; }
.modal-head > view { display: flex; align-items: baseline; }
.modal-title { color: #173f35; font-size: 35rpx; font-weight: 800; }
.tile-count { margin-left: 16rpx; color: #899590; font-size: 23rpx; }
.tile-count.ready { color: #1e7a57; font-weight: 700; }
.close { padding: 0 10rpx; color: #7e8984; font-size: 48rpx; line-height: 1; }
.mode-switch { flex-shrink: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 8rpx; margin: 0 8rpx 14rpx; padding: 7rpx; border-radius: 14rpx; background: #e2e5e0; }
.mode-button { height: 58rpx; line-height: 58rpx; text-align: center; border-radius: 10rpx; color: #68756f; font-size: 25rpx; }
.mode-button.active { background: #fff; color: #1e6b55; font-weight: 750; box-shadow: 0 3rpx 10rpx rgba(23,63,53,.12); }
.content-scroll { min-height: 0; flex: 1; }
.tile-wall { padding: 24rpx 12rpx 18rpx; border-radius: 20rpx; background: #fff; }
.rule-note { margin-top: 14rpx; padding: 14rpx 16rpx; border-radius: 12rpx; background: #fff3d8; color: #86662d; font-size: 19rpx; line-height: 1.45; }
.rule-note.standard-note { background: #e4eef8; color: #486982; }
.wall-row { display: grid; grid-template-columns: repeat(9, minmax(0, 1fr)); gap: 7rpx; margin-bottom: 12rpx; }
.wall-row:last-child { margin-bottom: 0; }
.wall-row.honors { grid-template-columns: repeat(7, minmax(0, 1fr)); padding: 0 62rpx; }
.mahjong-tile { position: relative; display: flex; align-items: center; justify-content: center; min-width: 0; aspect-ratio: 367 / 486; box-sizing: border-box; overflow: visible; }
.tile-image { width: 100%; height: 100%; display: block; }
.selector-tile.disabled { opacity: .28; }
.tile-badge { position: absolute; z-index: 2; right: -5rpx; top: -8rpx; min-width: 28rpx; height: 28rpx; line-height: 28rpx; text-align: center; border-radius: 14rpx; background: #19ad53; color: #fff; font-size: 18rpx; }
.hand-panel { margin-top: 18rpx; padding: 18rpx 14rpx; border-radius: 18rpx; background: #fff; }
.hand-head { display: flex; align-items: center; justify-content: space-between; }
.hand-title { color: #35443f; font-size: 26rpx; font-weight: 700; }
.mode-hints { display: flex; gap: 12rpx; color: #9ba39f; font-size: 20rpx; }
.mode-hints .active { color: #13a94d; font-weight: 700; }
.selected-hand { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 10rpx; margin-top: 16rpx; }
.empty-slot { min-width: 0; aspect-ratio: 367 / 486; box-sizing: border-box; border: 2rpx solid #e1e2df; border-radius: 8rpx; background: #fafafa; }
.result-panel { margin-top: 18rpx; padding: 20rpx; border-radius: 18rpx; background: #fff; }
.result-title { display: block; color: #173f35; font-size: 28rpx; font-weight: 800; }
.result-tiles { display: flex; flex-wrap: wrap; gap: 18rpx; margin-top: 18rpx; }
.result-tile-wrap { display: flex; flex-direction: column; align-items: center; color: #64716c; font-size: 19rpx; }
.tile-type { max-width: 150rpx; margin-top: 5rpx; color: #1e6b55; text-align: center; font-size: 18rpx; font-weight: 700; }
.result-tile { width: 62rpx; }
.discard-row { display: flex; align-items: center; min-height: 94rpx; padding: 15rpx 0; border-top: 1rpx solid #eceeeb; }
.discard-label { display: flex; align-items: center; gap: 8rpx; color: #b34b3f; font-size: 23rpx; }
.arrow { margin: 0 14rpx; color: #68746f; font-size: 22rpx; }
.wait-list { display: flex; flex-wrap: wrap; gap: 10rpx; flex: 1; }
.mini-wait { display: flex; flex-direction: column; align-items: center; }
.mini-tile { width: 44rpx; }
.mini-tile-type { max-width: 100rpx; margin-top: 5rpx; color: #1e6b55; text-align: center; font-size: 17rpx; font-weight: 700; line-height: 1.25; }
.lose-result { color: #a34c42; }
.actions { flex-shrink: 0; display: grid; grid-template-columns: 1fr 2fr; gap: 14rpx; margin-top: 14rpx; padding-top: 2rpx; }
.clear-button, .calculate-button { width: 100%; height: 78rpx; margin: 0; line-height: 78rpx; border-radius: 14rpx; font-size: 26rpx; }
.clear-button { background: #e5e8e3; color: #65716c; }
.calculate-button { background: #20bb55; color: #fff; }
.calculate-button[disabled] { background: #9fb4aa; color: #e9efec; }
</style>
