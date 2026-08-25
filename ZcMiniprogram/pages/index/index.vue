<template>
	<view class="page">
		<view class="topbar">
			<text class="brand">雀记</text>
			<view class="profile" @click="openProfileModal">
				<image v-if="profile?.avatarUrl" class="profile-avatar" :src="resolveFileUrl(profile.avatarUrl)" mode="aspectFill" />
				<view v-else class="profile-placeholder">我</view>
				<text class="profile-name">{{ profile?.nickname || '设置资料' }}</text>
			</view>
		</view>

		<view class="content">
			<view class="action-row">
				<button class="start-button" :loading="creating" :disabled="creating" @click="createGame">新建房间</button>
			</view>
			<view v-if="loading" class="loading-state">
				<view class="loading-spinner" />
				<text class="loading-text">正在加载房间</text>
			</view>
			<view v-else-if="!games.length" class="empty-card">
				<text class="empty-title">还没有房间</text>
				<text class="empty-text">新建房间后，把微信小程序码分享给朋友</text>
			</view>
			<view v-else class="status-list">
				<view v-for="statusGroup in statusGroups" :key="statusGroup.status" class="status-section">
					<text class="status-section-title">{{ statusGroup.title }}</text>
					<view v-for="group in statusGroup.yearGroups" :key="group.year" class="year-section">
						<view class="year-head">
							<view class="year-dot" />
							<text class="year-title">{{ group.year }}</text>
						</view>
						<view class="year-games">
							<view v-for="item in group.games" :key="item.id" class="timeline-item">
								<view class="timeline-dot" />
								<view class="swipe-row">
									<view class="delete-action" :class="{ revealed: swipedGameId === item.id }" @click.stop="removeGame(item)">删除</view>
									<view class="game-card" :class="{ swiped: swipedGameId === item.id }"
										@touchstart="startSwipe($event, item.id)" @touchmove="moveSwipe" @touchend="endSwipe(item.id)"
										@click="handleCardClick(item.id)">
										<view class="game-top">
											<view class="game-meta">
												<text class="game-time">{{ formatDate(item.status === 'FINISHED' ? item.finishedAt : item.createdAt) }}</text>
												<text class="game-code">房间 {{ item.joinCode }}</text>
											</view>
											<text class="my-score">{{ currentPlayer(item.players)?.currentScore ?? 0 }}</text>
											<text class="status" :class="item.status.toLowerCase()">{{ item.status === 'PLAYING' ? '进行中' : '已结束' }}</text>
										</view>
										<view class="players" :class="{ empty: !otherPlayers(item.players).length }">
											<view v-for="player in otherPlayers(item.players)" :key="player.id" class="player">
												<image v-if="player.avatarUrl" class="player-avatar" :src="resolveFileUrl(player.avatarUrl)" mode="aspectFill" />
												<view v-else class="player-avatar player-avatar-placeholder">{{ player.name.slice(0, 1) }}</view>
												<text class="player-summary">{{ player.name }}（{{ player.currentScore }}）</text>
											</view>
										</view>
									</view>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>
			<MahjongCalculator />
		</view>

		<view v-if="profileModalVisible" class="modal-mask" @click="closeProfileModal">
			<view class="profile-modal" @click.stop>
				<view class="modal-head">
					<text class="modal-title">{{ profile?.profileCompleted ? '编辑我的资料' : '先认识一下' }}</text>
					<text v-if="profile?.profileCompleted" class="modal-close" @click="closeProfileModal">×</text>
				</view>
				<text class="modal-subtitle">无需登录，头像和名字用于牌局展示</text>

				<!-- #ifdef MP-WEIXIN -->
				<button class="avatar-button" open-type="chooseAvatar" @chooseavatar="chooseWechatAvatar">
					<image v-if="profileDraft.avatarUrl" class="modal-avatar" :src="resolveFileUrl(profileDraft.avatarUrl)" mode="aspectFill" />
					<view v-else class="avatar-placeholder">选择头像</view>
				</button>
				<!-- #endif -->
				<!-- #ifndef MP-WEIXIN -->
				<button class="avatar-button" @click="chooseLocalAvatar">
					<image v-if="profileDraft.avatarUrl" class="modal-avatar" :src="resolveFileUrl(profileDraft.avatarUrl)" mode="aspectFill" />
					<view v-else class="avatar-placeholder">选择头像</view>
				</button>
				<!-- #endif -->

				<!-- #ifdef MP-WEIXIN -->
				<input v-model.trim="profileDraft.name" class="nickname-input" type="nickname" maxlength="20" placeholder="填写微信昵称" />
				<!-- #endif -->
				<!-- #ifndef MP-WEIXIN -->
				<input v-model.trim="profileDraft.name" class="nickname-input" maxlength="20" placeholder="填写你的名字" />
				<!-- #endif -->

				<button class="primary-button modal-submit" :loading="uploading" :disabled="uploading" @click="saveUserProfile">保存并开始使用</button>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow, onHide, onPullDownRefresh } from '@dcloudio/uni-app'
import { gameApi, resolveFileUrl, silentLogin, uploadAvatar, userApi } from '../../api/game.js'
import { clearCurrentGameId, consumeCurrentGameExit, getCurrentGameId, saveCurrentGameId, saveCurrentUser } from '../../utils/auth.js'
import { clearPendingInviteToken, getPendingInviteToken, saveInviteTokenFromOptions } from '../../utils/invite.js'
import MahjongCalculator from '../../components/MahjongCalculator.vue'

const games = ref([])
const statusGroups = computed(() => [
	{ status: 'PLAYING', title: '进行中', games: games.value.filter(game => game.status === 'PLAYING') },
	{ status: 'FINISHED', title: '已结束', games: games.value.filter(game => game.status === 'FINISHED') }
].filter(group => group.games.length).map(group => ({
	...group,
	yearGroups: groupGamesByYear(group.games, group.status === 'FINISHED' ? 'finishedAt' : 'createdAt')
})))
const loading = ref(true)
const initialized = ref(false)
const creating = ref(false)
const uploading = ref(false)
const profile = ref(null)
const profileModalVisible = ref(false)
const profileDraft = reactive({ name: '', avatarUrl: '' })
const swipedGameId = ref(null)
const swipeStartX = ref(0)
const swipeDeltaX = ref(0)
const swipeMoved = ref(false)
const pendingInviteToken = ref('')
let retryTimer = null
let retrying = false
let networkRetryNeeded = false

onLoad(options => readInviteToken(options))

onShow(() => {
	readInviteToken()
	initialize()
})
onHide(() => clearRetry())

function readInviteToken(options) {
	if (options) saveInviteTokenFromOptions(options)
	const inviteToken = getPendingInviteToken()
	if (inviteToken) pendingInviteToken.value = inviteToken
}

async function initialize() {
	const firstLoad = !initialized.value
	try {
		if (firstLoad) loading.value = true
		await silentLogin()
		profile.value = await userApi.me()
		saveCurrentUser(profile.value)
		if (!profile.value.profileCompleted) openProfileModal()
		games.value = await gameApi.list()
		if (profile.value.profileCompleted) {
			if (pendingInviteToken.value) await joinFromMiniProgramCode()
			else enterCurrentGame()
		}
		clearRetry()
	} catch (error) {
		if (isNetworkError(error)) {
			networkRetryNeeded = true
			scheduleRetry()
		} else uni.showToast({ title: String(error?.message || error || '操作失败'), icon: 'none' })
	} finally {
		initialized.value = true
		if (firstLoad) loading.value = false
	}
}

function isNetworkError(error) {
	return !error?.statusCode && /无法连接服务器|网络|request:fail/i.test(String(error?.message || error || ''))
}

function scheduleRetry() {
	if (retryTimer || retrying) return
	retryTimer = setTimeout(async () => {
		retryTimer = null
		retrying = true
		try {
			await initialize()
		} finally {
			retrying = false
			if (networkRetryNeeded) scheduleRetry()
		}
	}, 3000)
}

function clearRetry() {
	clearTimeout(retryTimer)
	retryTimer = null
	networkRetryNeeded = false
}

onPullDownRefresh(async () => {
	await loadGames()
	uni.stopPullDownRefresh()
})

async function loadGames() {
	try {
		loading.value = true
		games.value = await gameApi.list()
		clearRetry()
	} catch (error) {
		if (isNetworkError(error)) {
			networkRetryNeeded = true
			scheduleRetry()
		} else uni.showToast({ title: String(error?.message || error || '操作失败'), icon: 'none' })
	} finally {
		loading.value = false
	}
}

function openProfileModal() {
	profileDraft.name = profile.value?.nickname || ''
	profileDraft.avatarUrl = profile.value?.avatarUrl || ''
	profileModalVisible.value = true
}

function closeProfileModal() {
	if (profile.value?.profileCompleted) profileModalVisible.value = false
}

function chooseWechatAvatar(event) {
	profileDraft.avatarUrl = event.detail.avatarUrl
}

function chooseLocalAvatar() {
	uni.chooseImage({
		count: 1,
		sizeType: ['compressed'],
		success: result => { profileDraft.avatarUrl = result.tempFilePaths[0] }
	})
}

async function saveUserProfile() {
	if (!profileDraft.avatarUrl) {
		uni.showToast({ title: '请选择头像', icon: 'none' })
		return
	}
	if (!profileDraft.name) {
		uni.showToast({ title: '请填写名字', icon: 'none' })
		return
	}
	try {
		uploading.value = true
		if (!profileDraft.avatarUrl.startsWith('/uploads/') && !/^https?:\/\//.test(profileDraft.avatarUrl)) {
			profileDraft.avatarUrl = await uploadAvatar(profileDraft.avatarUrl)
		}
		profile.value = await userApi.update({
			nickname: profileDraft.name,
			avatarUrl: profileDraft.avatarUrl
		})
		saveCurrentUser(profile.value)
		profileModalVisible.value = false
		await joinFromMiniProgramCode()
	} catch (error) {
		uni.showToast({ title: String(error?.message || error || '操作失败'), icon: 'none' })
	} finally {
		uploading.value = false
	}
}

async function createGame() {
	if (!profile.value?.profileCompleted) {
		openProfileModal()
		return
	}
	try {
		creating.value = true
		const game = await gameApi.create()
		saveCurrentGameId(game.id)
		uni.navigateTo({ url: `/pages/game/game?id=${game.id}` })
	} catch (error) {
		uni.showToast({ title: String(error?.message || error || '创建失败'), icon: 'none' })
	} finally {
		creating.value = false
	}
}

async function joinFromMiniProgramCode() {
	if (!pendingInviteToken.value) return
	const inviteToken = pendingInviteToken.value
	try {
		const game = await gameApi.scanJoin(inviteToken)
		pendingInviteToken.value = ''
		clearPendingInviteToken()
		saveCurrentGameId(game.id)
		uni.navigateTo({ url: `/pages/game/game?id=${game.id}` })
	} catch (error) {
		uni.showToast({ title: String(error?.message || error || '扫码加入失败'), icon: 'none' })
	}
}

function startSwipe(event, gameId) {
	if (swipedGameId.value && swipedGameId.value !== gameId) swipedGameId.value = null
	swipeStartX.value = event.touches[0].clientX
	swipeDeltaX.value = 0
	swipeMoved.value = false
}

function moveSwipe(event) {
	swipeDeltaX.value = event.touches[0].clientX - swipeStartX.value
	if (Math.abs(swipeDeltaX.value) > 8) swipeMoved.value = true
}

function endSwipe(gameId) {
	if (swipeDeltaX.value < -45) swipedGameId.value = gameId
	if (swipeDeltaX.value > 35) swipedGameId.value = null
}

function handleCardClick(id) {
	if (swipeMoved.value) return
	if (swipedGameId.value === id) {
		swipedGameId.value = null
		return
	}
	openGame(id)
}

function removeGame(item) {
	uni.showModal({
		title: '删除房间记录',
		content: '确定删除这条房间记录吗？删除后不可恢复。',
		success: async result => {
			if (!result.confirm) return
			try {
				await gameApi.remove(item.id)
				games.value = games.value.filter(game => game.id !== item.id)
				swipedGameId.value = null
				uni.showToast({ title: '已删除', icon: 'success' })
			} catch (error) {
				uni.showToast({ title: String(error?.message || error || '删除失败'), icon: 'none' })
			}
		}
	})
}

function openGame(id) {
	const game = games.value.find(item => item.id === id)
	if (game?.status === 'PLAYING') saveCurrentGameId(id)
	uni.navigateTo({ url: `/pages/game/game?id=${id}` })
}

function enterCurrentGame() {
	const currentGameId = getCurrentGameId()
	if (!currentGameId || consumeCurrentGameExit(currentGameId)) return
	const currentGame = games.value.find(game => String(game.id) === String(currentGameId) && game.status === 'PLAYING')
	if (currentGame) uni.navigateTo({ url: `/pages/game/game?id=${currentGame.id}` })
	else clearCurrentGameId()
}

function currentPlayer(players) {
	return players.find(player => player.userId === profile.value?.id)
}

function otherPlayers(players) {
	return players
		.filter(player => player.userId !== profile.value?.id)
		.sort((left, right) => right.currentScore - left.currentScore)
}

function groupGamesByYear(games, timeField) {
	const groups = new Map()
	for (const game of [...games].sort((left, right) => new Date(right[timeField] || right.createdAt) - new Date(left[timeField] || left.createdAt))) {
		const year = new Date(game[timeField] || game.createdAt).getFullYear()
		if (!groups.has(year)) groups.set(year, [])
		groups.get(year).push(game)
	}
	return [...groups.entries()]
		.sort(([left], [right]) => right - left)
		.map(([year, yearGames]) => ({ year, games: yearGames }))
}

function formatDate(value) {
	if (!value) return ''
	const date = new Date(value)
	const pad = number => String(number).padStart(2, '0')
	return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; }
.topbar { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 34rpx 18rpx; background: #fff; border-bottom: 1rpx solid #e8ece9; }
.brand { color: #173f35; font-size: 38rpx; font-weight: 800; }
.profile { display: flex; align-items: center; max-width: 260rpx; }
.profile-avatar, .profile-placeholder { width: 58rpx; height: 58rpx; border-radius: 50%; }
.profile-placeholder { display: flex; align-items: center; justify-content: center; background: #dcebe5; color: #1e6b55; font-size: 23rpx; }
.profile-name { max-width: 170rpx; margin-left: 12rpx; color: #52635d; font-size: 24rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.content { padding: 24rpx 28rpx 70rpx; }
.action-row { margin-bottom: 30rpx; }
.start-button { height: 74rpx; margin: 0; padding: 0 8rpx; line-height: 74rpx; border-radius: 15rpx; background: #1e6b55; color: #fff; font-size: 25rpx; font-weight: 650; }
.room-section { margin-top: 30rpx; }
.room-section:first-of-type { margin-top: 0; }
.section-count { min-width: 42rpx; height: 42rpx; line-height: 42rpx; text-align: center; border-radius: 21rpx; background: #e6ece8; color: #61716b; font-size: 21rpx; }
.section-empty { padding: 32rpx 0; text-align: center; color: #a0a8a4; font-size: 23rpx; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; padding: 0 6rpx; }
.section-title { color: #173f35; font-size: 32rpx; font-weight: 750; }
.refresh { color: #6d7d77; font-size: 24rpx; }
.loading-state { min-height: 260rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #8b9691; }
.loading-spinner { width: 50rpx; height: 50rpx; box-sizing: border-box; border: 5rpx solid #d7e2dd; border-top-color: #1e6b55; border-radius: 50%; animation: loading-rotate .8s linear infinite; }
.loading-text { margin-top: 20rpx; font-size: 24rpx; }
@keyframes loading-rotate { to { transform: rotate(360deg); } }
.empty-card { display: flex; flex-direction: column; align-items: center; padding: 80rpx 24rpx; background: #fff; border-radius: 26rpx; }
.empty-title { color: #3b4b45; font-size: 30rpx; font-weight: 650; }
.empty-text { margin-top: 12rpx; color: #9aa39f; font-size: 24rpx; }
.status-list { margin-top: 4rpx; }
.status-section { margin-top: 42rpx; }
.status-section:first-child { margin-top: 0; }
.status-section-title { display: block; margin: 0 0 22rpx 6rpx; color: #173f35; font-size: 32rpx; font-weight: 750; }
.year-section { position: relative; }
.year-head { position: relative; height: 64rpx; display: flex; align-items: flex-start; padding-left: 42rpx; }
.year-head::after { content: ''; position: absolute; left: 13rpx; top: 28rpx; bottom: 0; width: 2rpx; background: #dfe5e1; }
.year-dot { position: absolute; z-index: 1; left: 0; top: 0; width: 28rpx; height: 28rpx; border-radius: 50%; background: #b8c3be; box-shadow: 0 0 0 7rpx #f4f1e8; }
.year-title { margin-top: -5rpx; color: #52635d; font-size: 32rpx; font-weight: 750; }
.year-games { position: relative; padding-left: 42rpx; }
.year-games::before { content: ''; position: absolute; left: 13rpx; top: 0; bottom: 0; width: 2rpx; background: #dfe5e1; }
.year-section:last-child .year-games::before { bottom: 28rpx; }
.timeline-item { position: relative; padding-bottom: 20rpx; }
.timeline-dot { position: absolute; z-index: 2; left: -36rpx; top: 28rpx; width: 16rpx; height: 16rpx; border-radius: 50%; background: #1e6b55; box-shadow: 0 0 0 5rpx #f4f1e8; }
.swipe-row { position: relative; border-radius: 24rpx; overflow: hidden; background: #fff; }
.delete-action { position: absolute; top: 0; right: 0; width: 132rpx; height: 100%; display: flex; align-items: center; justify-content: center; background: #fff; color: transparent; font-size: 27rpx; font-weight: 650; transition: background .22s ease, color .22s ease; }
.delete-action.revealed { background: #c94f45; color: #fff; }
.game-card { position: relative; z-index: 1; min-height: 196rpx; padding: 22rpx 24rpx; box-sizing: border-box; background: #fff; border-radius: 24rpx; box-shadow: 0 8rpx 28rpx rgba(23, 63, 53, .06); transform: translateX(0); transition: transform .22s ease; }
.game-card.swiped { transform: translateX(-132rpx); }
.game-top { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: start; }
.game-meta { min-width: 0; display: flex; flex-direction: column; }
.game-time { color: #3f514a; font-size: 24rpx; font-weight: 650; }
.game-code { margin-top: 6rpx; color: #1e6b55; font-size: 21rpx; }
.my-score { align-self: center; padding: 5rpx 12rpx; color: #263b34; font-size: 30rpx; font-weight: 800; }
.status { justify-self: end; margin-left: 12rpx; padding: 6rpx 13rpx; border-radius: 18rpx; font-size: 20rpx; }
.status.playing { background: #dcebe5; color: #1e6b55; }
.status.finished { background: #eeeae2; color: #877b69; }
.players { min-height: 50rpx; display: flex; flex-wrap: wrap; align-items: center; gap: 12rpx 22rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #edf0ec; }
.players.empty::before { content: ''; display: block; flex-basis: 100%; height: 0; }
.player { min-width: 0; display: flex; align-items: center; gap: 8rpx; }
.player-avatar { flex-shrink: 0; width: 42rpx; height: 42rpx; border-radius: 50%; }
.player-avatar-placeholder { display: flex; align-items: center; justify-content: center; background: #dcebe5; color: #1e6b55; font-size: 19rpx; }
.player-summary { max-width: 190rpx; color: #6c7974; font-size: 21rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.modal-mask { position: fixed; z-index: 999; inset: 0; display: flex; align-items: center; justify-content: center; padding: 42rpx; box-sizing: border-box; background: rgba(10, 30, 25, .68); }
.profile-modal, .join-modal { width: 100%; padding: 38rpx 34rpx 34rpx; box-sizing: border-box; border-radius: 30rpx; background: #fff; box-shadow: 0 24rpx 80rpx rgba(0,0,0,.25); }
.join-code-input { height: 96rpx; margin-top: 30rpx; padding: 0 24rpx; border-radius: 16rpx; background: #f2f4f1; color: #173f35; text-align: center; font-size: 42rpx; font-weight: 700; letter-spacing: 10rpx; }
.password-input { height: 84rpx; margin-top: 22rpx; padding: 0 24rpx; border-radius: 16rpx; background: #f2f4f1; text-align: center; font-size: 28rpx; }
.modal-head { display: flex; align-items: center; justify-content: space-between; }
.modal-title { color: #173f35; font-size: 38rpx; font-weight: 800; }
.modal-close { padding: 0 8rpx; color: #8b9691; font-size: 48rpx; line-height: 1; }
.modal-subtitle { display: block; margin-top: 10rpx; color: #899590; font-size: 23rpx; }
.avatar-button { width: 150rpx; height: 150rpx; margin-top: 34rpx; padding: 0; border-radius: 50%; background: #e8efeb; overflow: hidden; }
.modal-avatar { width: 100%; height: 100%; }
.avatar-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #527066; font-size: 23rpx; }
.nickname-input { height: 84rpx; margin-top: 28rpx; padding: 0 24rpx; border-radius: 16rpx; background: #f2f4f1; text-align: center; font-size: 29rpx; }
.modal-submit { height: 88rpx; line-height: 88rpx; margin-top: 28rpx; }
</style>
