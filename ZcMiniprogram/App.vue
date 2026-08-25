<script>
	import { clearCurrentGameExit } from './utils/auth.js'
	import { saveInviteTokenFromOptions } from './utils/invite.js'

	export default {
		onLaunch(options) {
			clearCurrentGameExit()
			saveInviteTokenFromOptions(options)
		},
		onShow(options) {
			saveInviteTokenFromOptions(options)
		}
	}
</script>

<style lang="scss">
	page {
		background: #f4f1e8;
		color: #1d2b27;
		font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
	}

	button::after {
		border: none;
	}

	.primary-button {
		background: #1e6b55;
		color: #fff;
		border-radius: 18rpx;
		font-size: 30rpx;
		font-weight: 600;
	}

	.ghost-button {
		background: #e4ece8;
		color: #1e6b55;
		border-radius: 18rpx;
		font-size: 28rpx;
	}
</style>
