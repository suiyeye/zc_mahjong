const INVITE_TOKEN_KEY = 'mahjong_pending_invite_token'

export function saveInviteTokenFromOptions(options) {
	const scene = decodeURIComponent(String(options?.scene || options?.query?.scene || '')).trim()
	if (/^[a-fA-F0-9]{32}$/.test(scene)) uni.setStorageSync(INVITE_TOKEN_KEY, scene)
}

export function getPendingInviteToken() {
	return uni.getStorageSync(INVITE_TOKEN_KEY) || ''
}

export function clearPendingInviteToken() {
	uni.removeStorageSync(INVITE_TOKEN_KEY)
}
