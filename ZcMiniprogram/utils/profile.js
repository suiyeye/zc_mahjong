const PROFILE_KEY = 'mahjong_user_profile'

export function getProfile() {
	return uni.getStorageSync(PROFILE_KEY) || null
}

export function saveProfile(profile) {
	uni.setStorageSync(PROFILE_KEY, {
		name: profile.name.trim(),
		avatarUrl: profile.avatarUrl || ''
	})
}

export function hasProfile() {
	const profile = getProfile()
	return Boolean(profile?.name && profile?.avatarUrl)
}
