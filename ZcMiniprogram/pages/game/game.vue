<template>
	<view v-if="game" class="page">
		<view class="room-head">
			<view>
				<text class="status">{{ game.status === 'PLAYING' ? '进行中' : '已结束' }}</text>
				<text class="room-code">房间号 {{ game.joinCode }}</text>
			</view>
			<view class="head-actions">
				<text class="member-count">{{ game.players.length }} 人</text>
			</view>
		</view>

		<text v-if="game.status === 'PLAYING'" class="tip">点击其他成员头像给分，下拉可刷新最新分数</text>

		<view class="member-grid">
			<view v-for="player in rankedPlayers" :key="player.id" class="member-card" :class="{ me: player.id === game.currentPlayerId }" @click="selectTarget(player)">
				<image v-if="player.avatarUrl" class="member-avatar" :src="resolveFileUrl(player.avatarUrl)" mode="aspectFill" />
				<view v-else class="avatar-placeholder">{{ player.name.slice(0, 1) }}</view>
				<text class="member-name">{{ player.name }}{{ player.id === game.currentPlayerId ? '（我）' : '' }}</text>
				<text class="member-score" :class="player.currentScore > 0 ? 'positive' : player.currentScore < 0 ? 'negative' : ''">{{ signed(player.currentScore) }}</text>
			</view>
			<view v-if="game.status === 'PLAYING'" class="member-card invite-member" @click="showInvite">
				<view class="invite-avatar">
					<view class="invite-horizontal" />
					<view class="invite-vertical" />
				</view>
				<text class="invite-member-name">添加好友</text>
			</view>
		</view>

		<view class="detail-card">
			<view v-if="!game.events?.length" class="empty">暂无房间明细</view>
			<view v-for="group in eventGroups" :key="group.key" class="event-group">
				<text class="event-time">{{ group.time }}</text>
				<view class="event-messages">
					<template v-for="event in group.events" :key="event.id">
						<view v-if="event.eventType === 'TRANSFER'" class="detail-row transfer-card">
							<view class="transfer-avatars">
								<image v-if="playerAvatar(event.playerId)" class="transfer-avatar" :class="{ 'me-avatar': event.playerId === game.currentPlayerId }" :src="playerAvatar(event.playerId)" mode="aspectFill" />
								<view v-else class="transfer-avatar placeholder" :class="{ 'me-avatar': event.playerId === game.currentPlayerId }">{{ playerInitial(event.playerId) }}</view>
								<text class="transfer-arrow">→</text>
								<image v-if="playerAvatar(event.targetPlayerId)" class="transfer-avatar" :class="{ 'me-avatar': event.targetPlayerId === game.currentPlayerId }" :src="playerAvatar(event.targetPlayerId)" mode="aspectFill" />
								<view v-else class="transfer-avatar placeholder" :class="{ 'me-avatar': event.targetPlayerId === game.currentPlayerId }">{{ playerInitial(event.targetPlayerId) }}</view>
							</view>
							<text class="transfer-amount">{{ event.amount }} 分</text>
							<view class="transfer-scores">
								<view class="transfer-score">
									<text class="ts-name">{{ event.playerName }}</text>
									<text class="ts-total">{{ event.senderScoreBefore }}</text>
									<text class="ts-delta negative">{{ signed(-event.amount) }} ▼</text>
								</view>
								<view class="transfer-score">
									<text class="ts-name">{{ event.targetPlayerName }}</text>
									<text class="ts-total">{{ event.targetScoreBefore }}</text>
									<text class="ts-delta positive">{{ signed(event.amount) }} ▲</text>
								</view>
							</view>
						</view>
						<view v-else class="detail-row notice-row">
							<view class="notice-avatar">
								<image v-if="playerAvatar(event.playerId)" class="notice-avatar-img" :src="playerAvatar(event.playerId)" mode="aspectFill" />
								<view v-else class="notice-avatar-img placeholder">{{ playerInitial(event.playerId) }}</view>
							</view>
							<text class="notice-text">{{ eventText(event) }}</text>
						</view>
					</template>
				</view>
			</view>
		</view>

		<MahjongCalculator />

		<view class="bottom-actions">
			<button class="summary-button" @click="summaryVisible = true">房间流水</button>
			<button class="mine-button" @click="mineVisible = true">房间管理</button>
		</view>

		<view v-if="summaryVisible" class="modal-mask sheet-mask" @click="summaryVisible = false">
			<view class="bottom-sheet mine-sheet" @click.stop>
				<view class="sheet-head">
					<text class="sheet-title">房间流水</text>
					<text class="sheet-close" @click="summaryVisible = false">×</text>
				</view>
				<view class="balance-card">
					<view class="balance-item"><text class="balance-label">我的收入</text><text class="income-value">+{{ mySummary.income }}</text></view>
					<view class="balance-item"><text class="balance-label">我的支出</text><text class="expense-value">-{{ mySummary.expense }}</text></view>
				</view>
				<view class="my-ledger-section">
					<scroll-view class="friend-filter" scroll-x>
						<view class="filter-row">
							<text class="filter-chip" :class="{ active: ledgerPlayerId === null }" @click="ledgerPlayerId = null">全部</text>
							<text v-for="player in game.players" :key="player.id" class="filter-chip" :class="{ active: ledgerPlayerId === player.id }" @click="ledgerPlayerId = player.id">{{ player.name }}</text>
						</view>
					</scroll-view>
					<scroll-view class="my-ledger-list" scroll-y>
						<view v-if="!displayedRounds.length" class="empty">暂无流水</view>
						<view v-for="group in ledgerGroups" :key="group.key" class="ledger-group">
							<text class="event-time">{{ group.time }}</text>
							<view class="ledger-messages">
								<view v-for="round in group.rounds" :key="round.id" class="record-row transfer-card">
									<view class="transfer-avatars">
										<image v-if="playerAvatar(senderScore(round).playerId)" class="transfer-avatar" :class="{ 'me-avatar': senderScore(round).playerId === game.currentPlayerId }" :src="playerAvatar(senderScore(round).playerId)" mode="aspectFill" />
										<view v-else class="transfer-avatar placeholder" :class="{ 'me-avatar': senderScore(round).playerId === game.currentPlayerId }">{{ playerInitial(senderScore(round).playerId) }}</view>
										<text class="transfer-arrow">→</text>
										<image v-if="playerAvatar(targetScore(round).playerId)" class="transfer-avatar" :class="{ 'me-avatar': targetScore(round).playerId === game.currentPlayerId }" :src="playerAvatar(targetScore(round).playerId)" mode="aspectFill" />
										<view v-else class="transfer-avatar placeholder" :class="{ 'me-avatar': targetScore(round).playerId === game.currentPlayerId }">{{ playerInitial(targetScore(round).playerId) }}</view>
									</view>
									<text class="transfer-amount">{{ transferAmount(round) }} 分</text>
									<view class="transfer-scores">
										<view class="transfer-score">
											<text class="ts-name">{{ senderScore(round).playerName }}</text>
											<text class="ts-total">{{ scoreBefore(senderScore(round)) }}</text>
											<text class="ts-delta negative">{{ signed(senderScore(round).delta) }} ▼</text>
										</view>
										<view class="transfer-score">
											<text class="ts-name">{{ targetScore(round).playerName }}</text>
											<text class="ts-total">{{ scoreBefore(targetScore(round)) }}</text>
											<text class="ts-delta positive">{{ signed(targetScore(round).delta) }} ▲</text>
										</view>
									</view>
								</view>
							</view>
						</view>
					</scroll-view>
				</view>
			</view>
		</view>

		<view v-if="mineVisible" class="modal-mask sheet-mask" @click="mineVisible = false">
			<view class="bottom-sheet mine-sheet" @click.stop>
				<view class="sheet-head">
					<text class="sheet-title">房间管理</text>
					<text class="sheet-close" @click="mineVisible = false">×</text>
				</view>
				<view class="mine-actions">
					<button v-if="game.status === 'FINISHED'" class="mine-action" @click="restart">继续开始</button>
					<button v-if="game.status === 'PLAYING'" class="mine-action danger" @click="finish">结束房间</button>
					<button class="mine-action" @click="manualVisible = true">使用手册</button>
				</view>
			</view>
		</view>

		<view v-if="manualVisible" class="modal-mask" @click="manualVisible = false">
			<view class="manual-modal" @click.stop>
				<view class="sheet-head">
					<text class="sheet-title">使用手册</text>
					<text class="sheet-close" @click="manualVisible = false">×</text>
				</view>
				<text class="manual-line">1. 点击“添加好友”，让好友扫码加入当前房间。</text>
				<text class="manual-line">2. 点击其他成员头像，输入分数后即可给分。</text>
				<text class="manual-line">3. 点击“房间流水”，查看各成员收入、支出和明细。</text>
				<text class="manual-line">4. 所有仍在房间内的成员均可结束或继续开始房间；退出房间后将立即离开。</text>
			</view>
		</view>

		<view v-if="inviteVisible" class="modal-mask" @click="inviteVisible = false">
			<view class="invite-modal" @click.stop>
				<text class="modal-title">微信扫码加入房间</text>
				<text class="modal-tip">让朋友使用微信扫一扫，即可进入雀记并加入房间</text>
				<image v-if="inviteQrCode" class="invite-qr" :src="inviteQrCode" mode="aspectFit" show-menu-by-longpress />
				<view v-else class="qr-loading">正在生成小程序码...</view>
				<text class="invite-code">房间号 {{ game.joinCode }}</text>
				<button class="cancel-button invite-close" @click="inviteVisible = false">关闭</button>
			</view>
		</view>

		<view v-if="targetPlayer" class="modal-mask" @click="closeTransfer">
			<view class="transfer-modal" @click.stop>
				<view class="target-info">
					<image v-if="targetPlayer.avatarUrl" class="target-avatar" :src="resolveFileUrl(targetPlayer.avatarUrl)" mode="aspectFill" />
					<view v-else class="target-avatar avatar-placeholder">{{ targetPlayer.name.slice(0, 1) }}</view>
					<text class="modal-title">给 {{ targetPlayer.name }} 分数</text>
				</view>
				<text class="modal-tip">分数会从你的总分扣除，并加到对方总分</text>
				<input v-model="amount" class="amount-input" type="number" placeholder="输入正数分值" />
				<view class="modal-actions">
					<button class="cancel-button" @click="closeTransfer">取消</button>
					<button class="confirm-button" :loading="submitting" :disabled="submitting" @click="submitTransfer">确认给分</button>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onBackPress, onLoad, onPullDownRefresh, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { gameApi, resolveFileUrl, roomSocketUrl } from '../../api/game.js'
import { clearCurrentGameId, getToken, markCurrentGameExited, saveCurrentGameId } from '../../utils/auth.js'
import MahjongCalculator from '../../components/MahjongCalculator.vue'

const gameId = ref(null)
const game = ref(null)
const targetPlayer = ref(null)
const amount = ref('')
const submitting = ref(false)
const inviteVisible = ref(false)
const inviteQrCode = ref('')
const summaryVisible = ref(false)
const mineVisible = ref(false)
const manualVisible = ref(false)
const ledgerPlayerId = ref(null)
let socketTask = null
let reconnectTimer = null
let realtimeActive = false
let refreshingFromSocket = false

const rankedPlayers = computed(() => game.value?.players || [])
const eventGroups = computed(() => {
	const groups = []
	for (const event of game.value?.events || []) {
		const eventTime = new Date(event.createdAt).getTime()
		const previous = groups[groups.length - 1]
		if (previous && Math.abs(eventTime - previous.lastTime) <= 3 * 60 * 1000) {
			previous.events.push(event)
			previous.lastTime = eventTime
		} else {
			groups.push({
				key: `${event.id}-${eventTime}`,
				time: formatEventTime(event.createdAt),
				lastTime: eventTime,
				events: [event]
			})
		}
	}
	return groups
})
const mySummary = computed(() => {
	if (!game.value) return { income: 0, expense: 0 }
	return game.value.rounds.reduce((summary, round) => {
		const score = round.scores.find(item => item.playerId === game.value.currentPlayerId)
		if (score?.delta > 0) summary.income += score.delta
		if (score?.delta < 0) summary.expense += Math.abs(score.delta)
		return summary
	}, { income: 0, expense: 0 })
})
const displayedRounds = computed(() => {
	if (!game.value) return []
	const allRounds = game.value.rounds
	if (ledgerPlayerId.value === null) return allRounds
	return allRounds.filter(round => round.scores.some(score => score.playerId === ledgerPlayerId.value && score.delta !== 0))
})
const ledgerGroups = computed(() => {
	const groups = []
	for (const round of displayedRounds.value) {
		const roundTime = new Date(round.createdAt).getTime()
		const previous = groups[groups.length - 1]
		if (previous && Math.abs(roundTime - previous.lastTime) <= 3 * 60 * 1000) {
			previous.rounds.push(round)
			previous.lastTime = roundTime
		} else {
			groups.push({
				key: `${round.id}-${roundTime}`,
				time: formatEventTime(round.createdAt),
				lastTime: roundTime,
				rounds: [round]
			})
		}
	}
	return groups
})

onLoad(options => {
	gameId.value = options.id
	loadGame().then(connectRoomSocket)
})

onShow(() => {
	if (gameId.value && !socketTask) connectRoomSocket()
})

onHide(() => disconnectRoomSocket())
onUnload(() => {
	markCurrentGameExited(gameId.value)
	disconnectRoomSocket()
})
onBackPress(() => {
	exitToHome()
	return true
})

onPullDownRefresh(async () => {
	await loadGame()
	uni.stopPullDownRefresh()
})

async function loadGame() {
	try {
		game.value = await gameApi.detail(gameId.value)
		if (game.value.status === 'PLAYING') saveCurrentGameId(game.value.id)
	} catch (error) {
		uni.showToast({ title: String(error?.message || error || '加载失败'), icon: 'none' })
	}
}

function connectRoomSocket() {
	realtimeActive = true
	if (!gameId.value || !getToken() || socketTask) return
	const token = getToken()
	socketTask = uni.connectSocket({
		url: roomSocketUrl(gameId.value, token),
		header: { Authorization: `Bearer ${token}` },
		fail: error => console.warn('房间实时连接创建失败', error)
	})
	socketTask.onOpen(() => console.log('房间实时连接已建立'))
	socketTask.onMessage(async message => {
		try {
			if (JSON.parse(message.data)?.type !== 'GAME_UPDATED' || refreshingFromSocket) return
			refreshingFromSocket = true
			await loadGame()
		} catch (_) {
		} finally {
			refreshingFromSocket = false
		}
	})
	socketTask.onClose(event => {
		console.warn('房间实时连接已关闭', event)
		socketTask = null
		if (!realtimeActive) return
		clearTimeout(reconnectTimer)
		reconnectTimer = setTimeout(connectRoomSocket, 2000)
	})
	socketTask.onError(error => {
		console.warn('房间实时连接失败', error)
		if (socketTask) socketTask.close()
	})
}

function disconnectRoomSocket() {
	realtimeActive = false
	clearTimeout(reconnectTimer)
	reconnectTimer = null
	if (socketTask) socketTask.close()
	socketTask = null
}

function involvedScores(round) {
	return round.scores.filter(score => score.delta !== 0)
}

function senderScore(round) {
	const scores = involvedScores(round)
	return scores.find(score => score.delta < 0) || scores[0] || { playerId: null, playerName: '', scoreAfter: 0, delta: 0 }
}

function targetScore(round) {
	const scores = involvedScores(round)
	return scores.find(score => score.delta > 0) || scores[1] || { playerId: null, playerName: '', scoreAfter: 0, delta: 0 }
}

function transferAmount(round) {
	return Math.abs(targetScore(round)?.delta || 0)
}

function scoreBefore(score) {
	return (score?.scoreAfter || 0) - (score?.delta || 0)
}

function playerById(playerId) {
	return game.value?.players.find(player => player.id === playerId)
}

function playerAvatar(playerId) {
	const player = playerById(playerId)
	return player?.avatarUrl ? resolveFileUrl(player.avatarUrl) : ''
}

function playerInitial(playerId) {
	const player = playerById(playerId)
	return player?.name?.slice(0, 1) || ''
}

function exitToHome() {
	markCurrentGameExited(gameId.value)
	disconnectRoomSocket()
	uni.reLaunch({ url: '/pages/index/index' })
}

function exitRoom() {
	uni.showModal({
		title: '退出房间',
		content: '确定退出当前房间吗？',
		success: result => {
			if (result.confirm) exitToHome()
		}
	})
}

function restart() {
	uni.showModal({
		title: '继续开始',
		content: '确定继续开始这个房间吗？',
		success: async result => {
			if (!result.confirm) return
			try {
				game.value = await gameApi.restart(gameId.value)
				saveCurrentGameId(game.value.id)
				mineVisible.value = false
				uni.showToast({ title: '房间已继续开始', icon: 'success' })
			} catch (error) {
				uni.showToast({ title: String(error?.message || error || '操作失败'), icon: 'none' })
			}
		}
	})
}

function signed(value) {
	const number = Number(value) || 0
	return number > 0 ? `+${number}` : `${number}`
}

function selectTarget(player) {
	if (game.value.status !== 'PLAYING') return
	if (player.id === game.value.currentPlayerId) return
	targetPlayer.value = player
	amount.value = ''
}

function closeTransfer() {
	if (!submitting.value) targetPlayer.value = null
}

async function submitTransfer() {
	const score = Number(amount.value)
	if (!Number.isInteger(score) || score <= 0 || score > 1000000) {
		uni.showToast({ title: '请输入 1 至 1000000 的整数', icon: 'none' })
		return
	}
	try {
		submitting.value = true
		game.value = await gameApi.transfer(gameId.value, targetPlayer.value.id, score)
		targetPlayer.value = null
		uni.showToast({ title: '给分成功', icon: 'success' })
	} catch (error) {
		uni.showToast({ title: String(error?.message || error || '给分失败'), icon: 'none' })
	} finally {
		submitting.value = false
	}
}

async function showInvite() {
	inviteVisible.value = true
	if (inviteQrCode.value) return
	try {
		const invite = await gameApi.invite(gameId.value)
		inviteQrCode.value = invite.miniProgramCodeDataUrl
	} catch (error) {
		inviteVisible.value = false
		uni.showToast({ title: String(error?.message || error || '小程序码生成失败'), icon: 'none' })
	}
}

function finish() {
	uni.showModal({
		title: '结束房间',
		content: '结束后所有成员都不能继续给分，确定结束吗？',
		success: async result => {
			if (!result.confirm) return
			try {
				game.value = await gameApi.finish(gameId.value)
				clearCurrentGameId()
				mineVisible.value = false
				uni.showToast({ title: '房间已结束', icon: 'success' })
			} catch (error) {
				uni.showToast({ title: String(error?.message || error || '操作失败'), icon: 'none' })
			}
		}
	})
}

function eventText(event) {
	if (event.eventType === 'JOIN') return `${event.playerName} 进入房间`
	if (event.eventType === 'LEAVE') return `${event.playerName} 退出房间`
	return `${event.playerName} → ${event.targetPlayerName} ${event.amount} 分`
}

function formatEventTime(value) {
	const date = new Date(value)
	const pad = number => String(number).padStart(2, '0')
	return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 28rpx 28rpx calc(150rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.room-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.room-head > view { display: flex; flex-direction: column; }
.status { width: fit-content; padding: 6rpx 14rpx; border-radius: 18rpx; background: #dcebe5; color: #1e6b55; font-size: 22rpx; }
.room-code { margin-top: 12rpx; color: #173f35; font-size: 38rpx; font-weight: 800; }
.head-actions { display: flex; flex-direction: column; align-items: flex-end; }
.member-count { color: #7c8883; font-size: 26rpx; }
.tip { display: block; margin-bottom: 24rpx; color: #7a8882; font-size: 23rpx; }
.member-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10rpx; }
.member-card { display: flex; flex-direction: column; align-items: center; padding: 14rpx 2rpx 12rpx; }
.member-card.me .member-avatar, .member-card.me .avatar-placeholder { box-shadow: 0 0 0 3rpx #4d927d; }
.member-avatar, .avatar-placeholder, .invite-avatar { width: 76rpx; height: 76rpx; border-radius: 50%; }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; background: #dcebe5; color: #1e6b55; font-size: 30rpx; }
.member-name { width: 100%; margin-top: 10rpx; text-align: center; color: #53625d; font-size: 20rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.member-score { margin-top: 4rpx; color: #243a33; font-size: 28rpx; font-weight: 800; }
.invite-member { justify-content: flex-start; }
.invite-avatar { position: relative; box-sizing: border-box; border: 3rpx solid #c8cfcc; }
.invite-horizontal, .invite-vertical { position: absolute; left: 50%; top: 50%; border-radius: 2rpx; background: #aab3af; transform: translate(-50%, -50%); }
.invite-horizontal { width: 34rpx; height: 3rpx; }
.invite-vertical { width: 3rpx; height: 34rpx; }
.invite-member-name { margin-top: 10rpx; color: #68746f; font-size: 20rpx; }
.positive { color: #1e7a57; }
.negative { color: #b95043; }
.detail-card { min-height: 300rpx; margin-top: 30rpx; padding: 20rpx 24rpx; box-sizing: border-box; border-radius: 16px; background: #f8f7f4; box-shadow: inset 0 2px 14px rgba(0, 0, 0, 0.035), 0 2px 8px rgba(0, 0, 0, 0.03); }
.detail-card .empty { min-height: 260rpx; display: flex; align-items: center; justify-content: center; padding: 0; }
.event-group { margin-top: 28rpx; }
.event-group:first-child { margin-top: 0; }
.event-time { display: block; margin-bottom: 12rpx; color: #a0a8a4; text-align: left; font-size: 19rpx; white-space: nowrap; }
.event-messages { padding: 0; }
.detail-row { border-top: 1rpx solid rgba(113, 120, 110, .12); }
.detail-row:first-child { border-top: 0; }
.bottom-actions { position: fixed; z-index: 900; left: 0; right: 0; bottom: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; padding: 16rpx 28rpx calc(16rpx + env(safe-area-inset-bottom)); background: rgba(244, 241, 232, .96); border-top: 1rpx solid #e1e5e1; }
.summary-button, .mine-button { height: 82rpx; margin: 0; line-height: 82rpx; border-radius: 16rpx; font-size: 28rpx; font-weight: 700; }
.summary-button { background: #1e6b55; color: #fff; }
.mine-button { background: #e2ece7; color: #1e6b55; }
.modal-mask { position: fixed; z-index: 999; inset: 0; display: flex; align-items: center; justify-content: center; padding: 42rpx; box-sizing: border-box; background: rgba(10, 30, 25, .68); }
.sheet-mask { align-items: flex-end; padding: 0; }
.bottom-sheet { width: 100%; max-height: 76vh; padding: 30rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 30rpx 30rpx 0 0; background: #fff; }
.mine-sheet { background: #f4f1e8; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.sheet-title { color: #173f35; font-size: 32rpx; font-weight: 750; }
.sheet-close { padding: 0 8rpx; color: #8c9692; font-size: 44rpx; line-height: 1; }
.friend-row { display: grid; grid-template-columns: 68rpx minmax(0, 1fr) auto; align-items: center; gap: 16rpx; padding: 18rpx 4rpx; border-top: 1rpx solid #edf0ec; }
.friend-avatar { width: 68rpx; height: 68rpx; border-radius: 50%; }
.friend-name { color: #43534d; font-size: 26rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.friend-score { color: #263b34; font-size: 27rpx; font-weight: 750; }
.balance-card { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; }
.balance-item { display: flex; flex-direction: column; align-items: center; padding: 28rpx 12rpx; border-radius: 20rpx; background: #fff; }
.balance-label { color: #7b8782; font-size: 23rpx; }
.income-value, .expense-value { margin-top: 10rpx; font-size: 36rpx; font-weight: 800; }
.income-value { color: #1e7a57; }
.expense-value { color: #b95043; }
.my-ledger-section { min-height: 0; display: flex; flex-direction: column; margin-top: 18rpx; }
.friend-filter { width: 100%; margin-bottom: 14rpx; white-space: nowrap; }
.filter-row { display: inline-flex; gap: 12rpx; padding: 2rpx; }
.filter-chip { padding: 10rpx 20rpx; border-radius: 24rpx; background: #edf0ed; color: #68756f; font-size: 22rpx; }
.filter-chip.active { background: #1e6b55; color: #fff; }
.my-ledger-list { max-height: 46vh; }
.ledger-group { margin-top: 28rpx; }
.ledger-group:first-child { margin-top: 0; }
.ledger-messages { padding: 0; }
.mine-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; margin-top: 20rpx; }
.mine-action { height: 76rpx; margin: 0; line-height: 76rpx; border-radius: 14rpx; background: #fff; color: #43534d; font-size: 24rpx; }
.mine-action.danger { color: #a34d43; }
.manual-modal { width: 100%; max-height: 80vh; padding: 30rpx 28rpx; box-sizing: border-box; border-radius: 28rpx; background: #fff; }
.empty { padding: 34rpx 0; text-align: center; color: #9aa39f; font-size: 25rpx; }
.record-row { border-top: 1rpx solid #edf0ec; }
.record-row:first-child { border-top: 0; }
.transfer-card { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 22rpx 24rpx; margin-bottom: 16rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 4rpx 16rpx rgba(0,0,0,.04); }
.event-messages .transfer-card { margin-bottom: 14rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.04); }
.event-messages .transfer-card:last-child { margin-bottom: 0; }
.ledger-messages .transfer-card:last-child { margin-bottom: 0; }
.notice-row { display: flex; align-items: center; gap: 16rpx; padding: 14rpx 24rpx; margin-bottom: 12rpx; border-radius: 16rpx; background: #fff; }
.notice-row:last-child { margin-bottom: 0; }
.notice-avatar { flex-shrink: 0; display: flex; align-items: center; justify-content: center; width: 56rpx; height: 56rpx; border-radius: 50%; border: 3rpx solid #cfd5d1; box-sizing: border-box; background: #fff; }
.notice-avatar-img { width: 46rpx; height: 46rpx; border-radius: 50%; background: #e8ede9; }
.notice-avatar-img.placeholder { display: flex; align-items: center; justify-content: center; color: #68746f; font-size: 22rpx; font-weight: 600; }
.notice-text { color: #707b76; font-size: 24rpx; }
.transfer-avatars { flex-shrink: 0; display: flex; align-items: center; gap: 8rpx; }
.transfer-avatar { width: 58rpx; height: 58rpx; border-radius: 50%; border: 3rpx solid #cfd5d1; box-sizing: border-box; background: #e8ede9; }
.transfer-avatar.placeholder { display: flex; align-items: center; justify-content: center; color: #68746f; font-size: 24rpx; font-weight: 600; }
.transfer-avatar.me-avatar { border-color: #4d927d; box-shadow: 0 0 0 1rpx #4d927d; }
.transfer-arrow { color: #99a39e; font-size: 24rpx; }
.transfer-amount { flex: 1; min-width: 0; color: #2f3b37; font-size: 30rpx; font-weight: 700; text-align: center; }
.transfer-scores { flex-shrink: 0; display: flex; align-items: center; gap: 22rpx; }
.transfer-score { display: flex; flex-direction: column; align-items: center; min-width: 72rpx; }
.ts-name { max-width: 100rpx; color: #6d7974; font-size: 19rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.ts-total { margin-top: 4rpx; color: #2f3b37; font-size: 26rpx; font-weight: 700; }
.ts-delta { margin-top: 2rpx; font-size: 19rpx; }
.manual-line { display: block; margin-top: 20rpx; color: #53625d; font-size: 25rpx; line-height: 1.65; }
.transfer-modal, .invite-modal { width: 100%; padding: 36rpx 32rpx 30rpx; box-sizing: border-box; border-radius: 28rpx; background: #fff; }
.invite-modal { display: flex; flex-direction: column; align-items: center; }
.invite-qr { width: 420rpx; height: 420rpx; margin-top: 24rpx; }
.qr-loading { height: 300rpx; display: flex; align-items: center; color: #919b97; font-size: 24rpx; }
.invite-code { margin-top: 16rpx; color: #173f35; font-size: 28rpx; font-weight: 700; }
.invite-close { width: 100%; margin-top: 26rpx; }
.target-info { display: flex; align-items: center; }
.target-avatar { width: 76rpx; height: 76rpx; margin-right: 18rpx; border-radius: 50%; }
.modal-title { color: #173f35; font-size: 34rpx; font-weight: 750; }
.modal-tip { display: block; margin-top: 18rpx; color: #899590; font-size: 23rpx; }
.amount-input { height: 86rpx; margin-top: 24rpx; padding: 0 22rpx; border-radius: 15rpx; background: #f3f4f1; text-align: center; font-size: 32rpx; }
.modal-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; margin-top: 28rpx; }
.cancel-button, .confirm-button { height: 82rpx; line-height: 82rpx; border-radius: 15rpx; font-size: 27rpx; }
.cancel-button { background: #ecefeb; color: #65716c; }
.confirm-button { background: #1e6b55; color: #fff; }
</style>
