const AUTH_TOKEN_KEY = 'mahjong_auth_token'
const USER_KEY = 'mahjong_current_user'
const CURRENT_GAME_KEY = 'mahjong_current_game_id'
const EXITED_GAME_KEY = 'mahjong_exited_game_id'

export function getToken() {
	return uni.getStorageSync(AUTH_TOKEN_KEY) || ''
}

export function saveSession(session) {
	uni.setStorageSync(AUTH_TOKEN_KEY, session.token)
	uni.setStorageSync(USER_KEY, session.user)
}

export function clearSession() {
	uni.removeStorageSync(AUTH_TOKEN_KEY)
	uni.removeStorageSync(USER_KEY)
}

export function getCurrentUser() {
	return uni.getStorageSync(USER_KEY) || null
}

export function saveCurrentUser(user) {
	uni.setStorageSync(USER_KEY, user)
}

export function getCurrentGameId() {
	return uni.getStorageSync(CURRENT_GAME_KEY) || ''
}

export function saveCurrentGameId(gameId) {
	uni.setStorageSync(CURRENT_GAME_KEY, String(gameId))
}

export function clearCurrentGameId() {
	uni.removeStorageSync(CURRENT_GAME_KEY)
}

export function markCurrentGameExited(gameId) {
	uni.setStorageSync(EXITED_GAME_KEY, String(gameId))
}

export function consumeCurrentGameExit(gameId) {
	return String(uni.getStorageSync(EXITED_GAME_KEY) || '') === String(gameId)
}

export function clearCurrentGameExit() {
	uni.removeStorageSync(EXITED_GAME_KEY)
}
