import { clearSession, getToken, saveSession } from '../utils/auth.js'

const BASE_URL = 'https://panmen.top'
const MINI_PROGRAM_URL = `${BASE_URL}/zcmini`
const API_URL = `${MINI_PROGRAM_URL}/api`
const SOCKET_URL = MINI_PROGRAM_URL.replace(/^https/, 'wss').replace(/^http/, 'ws')
let loginPromise = null

export function roomSocketUrl(gameId, token) {
	return `${SOCKET_URL}/ws/games/${gameId}?token=${encodeURIComponent(token)}`
}

export function resolveFileUrl(url) {
	if (!url || /^https?:\/\//.test(url) || url.startsWith('wxfile://') || url.startsWith('blob:')) return url
	if (url.startsWith('/uploads/')) return `${MINI_PROGRAM_URL}${url}`
	return `${BASE_URL}${url}`
}

function rawRequest(options) {
	return new Promise((resolve, reject) => {
		uni.request({
			url: `${API_URL}${options.url}`,
			method: options.method || 'GET',
			data: options.data,
			header: {
				'content-type': 'application/json',
				...(options.auth === false || !getToken() ? {} : { Authorization: `Bearer ${getToken()}` })
			},
			success: response => {
				if (response.statusCode >= 200 && response.statusCode < 300) {
					resolve(response.data)
					return
				}
				const error = new Error(response.data?.message || '请求失败')
				error.statusCode = response.statusCode
				reject(error)
			},
			fail: () => reject(new Error('无法连接服务器，请检查后端是否启动'))
		})
	})
}

export function silentLogin(force = false) {
	if (!force && getToken()) return Promise.resolve(null)
	if (loginPromise) return loginPromise
	loginPromise = new Promise((resolve, reject) => {
		uni.login({
			success: async result => {
				try {
					if (!result.code) throw new Error('未获取到微信登录凭证')
					const session = await rawRequest({
						url: '/auth/wechat-login',
						method: 'POST',
						data: { code: result.code },
						auth: false
					})
					saveSession(session)
					resolve(session)
				} catch (error) {
					reject(error)
				}
			},
			fail: () => reject(new Error('微信静默登录失败'))
		})
	}).finally(() => { loginPromise = null })
	return loginPromise
}

export async function request(options) {
	if (options.auth !== false && !getToken()) await silentLogin()
	try {
		return await rawRequest(options)
	} catch (error) {
		if (options.auth !== false && error.statusCode === 401 && !options.retried) {
			clearSession()
			await silentLogin(true)
			return rawRequest({ ...options, retried: true })
		}
		throw error
	}
}

export function uploadAvatar(filePath) {
	return new Promise(async (resolve, reject) => {
		try {
			if (!getToken()) await silentLogin()
			uni.uploadFile({
				url: `${API_URL}/files/avatars`,
				filePath,
				name: 'file',
				header: { Authorization: `Bearer ${getToken()}` },
				success: response => {
					let data
					try { data = JSON.parse(response.data) } catch (_) { data = {} }
					if (response.statusCode >= 200 && response.statusCode < 300) {
						resolve(data.url)
						return
					}
					reject(new Error(data.message || '头像上传失败'))
				},
				fail: () => reject(new Error('头像上传失败，请检查网络'))
			})
		} catch (error) {
			reject(error)
		}
	})
}

export const authApi = {
	login: () => silentLogin(true)
}

export const userApi = {
	me: () => request({ url: '/users/me' }),
	update: data => request({ url: '/users/me', method: 'PUT', data })
}

export const gameApi = {
	list: () => request({ url: '/games' }),
	detail: id => request({ url: `/games/${id}` }),
	create: () => request({ url: '/games', method: 'POST' }),
	scanJoin: inviteToken => request({ url: '/games/scan-join', method: 'POST', data: { inviteToken } }),
	invite: id => request({ url: `/games/${id}/invite` }),
	transfer: (id, targetPlayerId, amount) => request({ url: `/games/${id}/transfers`, method: 'POST', data: { targetPlayerId, amount } }),
	remove: id => request({ url: `/games/${id}`, method: 'DELETE' }),
	leave: id => request({ url: `/games/${id}/leave`, method: 'POST' }),
	restart: id => request({ url: `/games/${id}/restart`, method: 'POST' }),
	finish: id => request({ url: `/games/${id}/finish`, method: 'POST' })
}
