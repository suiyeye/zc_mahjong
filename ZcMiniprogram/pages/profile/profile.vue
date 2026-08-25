<template>
	<view class="page">
		<view class="tile">發</view>
		<text class="title">欢迎来到雀记</text>
		<text class="subtitle">先设置你的头像和名字，开局时会自动带入</text>

		<view class="profile-card">
			<!-- #ifdef MP-WEIXIN -->
			<button class="avatar-button" open-type="chooseAvatar" @chooseavatar="chooseWechatAvatar">
				<image v-if="profile.avatarUrl" class="avatar" :src="resolveFileUrl(profile.avatarUrl)" mode="aspectFill" />
				<view v-else class="avatar-placeholder">选择头像</view>
			</button>
			<!-- #endif -->
			<!-- #ifndef MP-WEIXIN -->
			<button class="avatar-button" @click="chooseLocalAvatar">
				<image v-if="profile.avatarUrl" class="avatar" :src="resolveFileUrl(profile.avatarUrl)" mode="aspectFill" />
				<view v-else class="avatar-placeholder">选择头像</view>
			</button>
			<!-- #endif -->

			<!-- #ifdef MP-WEIXIN -->
			<input v-model.trim="profile.name" class="nickname-input" type="nickname" maxlength="20" placeholder="填写微信昵称" />
			<!-- #endif -->
			<!-- #ifndef MP-WEIXIN -->
			<input v-model.trim="profile.name" class="nickname-input" maxlength="20" placeholder="填写你的名字" />
			<!-- #endif -->
		</view>

		<button class="primary-button submit" :loading="uploading" :disabled="uploading" @click="submit">进入雀记</button>
		<text class="tip">头像和名字仅用于对局展示，可随时修改</text>
	</view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProfile, saveProfile } from '../../utils/profile.js'
import { resolveFileUrl, uploadAvatar } from '../../api/game.js'

const profile = reactive({ name: '', avatarUrl: '' })
const uploading = ref(false)

onLoad(options => {
	const current = getProfile()
	if (current) Object.assign(profile, current)
	if (options.edit !== '1' && current?.name && current?.avatarUrl) {
		uni.redirectTo({ url: '/pages/index/index' })
	}
})

function chooseWechatAvatar(event) {
	profile.avatarUrl = event.detail.avatarUrl
}

function chooseLocalAvatar() {
	uni.chooseImage({
		count: 1,
		sizeType: ['compressed'],
		success: result => { profile.avatarUrl = result.tempFilePaths[0] }
	})
}

async function submit() {
	if (!profile.avatarUrl) {
		uni.showToast({ title: '请选择头像', icon: 'none' })
		return
	}
	if (!profile.name) {
		uni.showToast({ title: '请填写名字', icon: 'none' })
		return
	}
	try {
		uploading.value = true
		if (!profile.avatarUrl.startsWith('/uploads/') && !/^https?:\/\//.test(profile.avatarUrl)) {
			profile.avatarUrl = await uploadAvatar(profile.avatarUrl)
		}
		saveProfile(profile)
		uni.reLaunch({ url: '/pages/index/index' })
	} catch (error) {
		uni.showToast({ title: error.message, icon: 'none' })
	} finally {
		uploading.value = false
	}
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 100rpx 46rpx 60rpx; box-sizing: border-box; display: flex; flex-direction: column; align-items: center; background: #173f35; }
.tile { width: 104rpx; height: 124rpx; line-height: 124rpx; text-align: center; border-radius: 16rpx; background: #f8f3e7; box-shadow: inset 0 0 0 7rpx #e4dbc8; color: #26725b; font-size: 54rpx; font-weight: 800; }
.title { margin-top: 42rpx; color: #fff; font-size: 48rpx; font-weight: 800; }
.subtitle { margin-top: 14rpx; color: #b8ccc5; font-size: 25rpx; text-align: center; }
.profile-card { width: 100%; margin-top: 52rpx; padding: 46rpx 32rpx 36rpx; box-sizing: border-box; border-radius: 28rpx; background: #fff; }
.avatar-button { width: 156rpx; height: 156rpx; padding: 0; border-radius: 50%; background: #e8efeb; overflow: hidden; }
.avatar { width: 100%; height: 100%; }
.avatar-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #527066; font-size: 24rpx; }
.nickname-input { height: 88rpx; margin-top: 34rpx; padding: 0 24rpx; border-radius: 16rpx; background: #f2f4f1; text-align: center; font-size: 30rpx; }
.submit { width: 100%; height: 94rpx; line-height: 94rpx; margin-top: 38rpx; background: #dca95e; color: #173f35; }
.tip { margin-top: 24rpx; color: #91aaa1; font-size: 22rpx; }
</style>
