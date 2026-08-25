const TILE_SYMBOLS = [
	'🀇', '🀈', '🀉', '🀊', '🀋', '🀌', '🀍', '🀎', '🀏',
	'🀐', '🀑', '🀒', '🀓', '🀔', '🀕', '🀖', '🀗', '🀘',
	'🀙', '🀚', '🀛', '🀜', '🀝', '🀞', '🀟', '🀠', '🀡',
	'🀀', '🀁', '🀂', '🀃', '🀄', '🀅', '🀆'
]

const HONOR_IMAGE_NUMBERS = ['03_东', '04_南', '05_西', '06_北', '01_中', '02_发', '07_白']

function tileImagePath(id) {
	if (id < 9) return `/static/mahjong-imgs/万_${String(id + 1).padStart(2, '0')}.png`
	if (id < 18) return `/static/mahjong-imgs/条_${String(id - 8).padStart(2, '0')}.png`
	if (id < 27) return `/static/mahjong-imgs/筒_${String(id - 17).padStart(2, '0')}.png`
	return `/static/mahjong-imgs/字牌_${HONOR_IMAGE_NUMBERS[id - 27]}.png`
}

export const TILE_GROUPS = [
	{
		name: '筒',
		tiles: Array.from({ length: 9 }, (_, index) => makeTile(index + 18, `${index + 1}筒`, `${index + 1}筒`))
	},
	{
		name: '条',
		tiles: Array.from({ length: 9 }, (_, index) => makeTile(index + 9, `${index + 1}条`, `${index + 1}条`))
	},
	{
		name: '万',
		tiles: Array.from({ length: 9 }, (_, index) => makeTile(index, `${index + 1}万`, `${index + 1}万`))
	},
	{
		name: '字',
		tiles: [
			makeTile(27, '东风', '东'),
			makeTile(28, '南风', '南'),
			makeTile(29, '西风', '西'),
			makeTile(30, '北风', '北'),
			makeTile(31, '红中', '中'),
			makeTile(32, '发财', '发'),
			makeTile(33, '白板', '白')
		]
	}
]

export const ALL_TILES = Array.from({ length: 34 }, (_, id) => {
	const groupTile = TILE_GROUPS.flatMap(group => group.tiles).find(tile => tile.id === id)
	return groupTile
})

function makeTile(id, label, short) {
	return { id, label, short, symbol: TILE_SYMBOLS[id], image: tileImagePath(id) }
}

export function tileName(id) {
	return ALL_TILES[id]?.label || ''
}

export function tileShortName(id) {
	return ALL_TILES[id]?.short || ''
}

export function tileSymbol(id) {
	return ALL_TILES[id]?.symbol || ''
}

export function tileImage(id) {
	return ALL_TILES[id]?.image || ''
}

export function analyzeTiles(tileIds, mode = 'sichuan') {
	if (!Array.isArray(tileIds) || ![13, 14].includes(tileIds.length)) {
		return { mode: 'invalid', message: '请选择 13 张或 14 张牌' }
	}
	const validation = validateTiles(tileIds, mode)
	if (validation) return { mode: 'invalid', message: validation }

	if (tileIds.length === 13) {
		const waits = findWinningTiles(tileIds, mode)
		const waitDetails = waits.map(tileId => ({
			tileId,
			...analyzeWinningHand([...tileIds, tileId], mode)
		}))
		return waits.length
			? { mode: 'waiting', waits, waitDetails }
			: { mode: 'not-waiting', message: '当前 13 张牌还没有听牌' }
	}

	const win = analyzeWinningHand(tileIds, mode)
	if (win.win) return { mode: 'win', ...win }

	const discards = findDiscardOptions(tileIds, mode)
	return discards.length
		? { mode: 'discard', discards }
		: { mode: 'not-waiting', message: '当前牌型没有一打即听的选择' }
}

export function analyzeHand(tileIds, mode = 'sichuan') {
	if (!Array.isArray(tileIds) || tileIds.length !== 14) {
		return { win: false, message: '请选择 14 张牌' }
	}
	const validation = validateTiles(tileIds, mode)
	if (validation) return { win: false, message: validation }
	return analyzeWinningHand(tileIds, mode)
}

export function findWinningTiles(tileIds, mode = 'sichuan') {
	if (!Array.isArray(tileIds) || tileIds.length !== 13 || validateTiles(tileIds, mode)) return []
	const counts = toCounts(tileIds)
	const waits = []
	for (let candidate = 0; candidate < 34; candidate++) {
		if (mode === 'sichuan' && candidate >= 27) continue
		if (counts[candidate] >= 4) continue
		if (analyzeWinningHand([...tileIds, candidate], mode).win) waits.push(candidate)
	}
	return waits
}

export function findDiscardOptions(tileIds, mode = 'sichuan') {
	if (!Array.isArray(tileIds) || tileIds.length !== 14 || validateTiles(tileIds, mode)) return []
	const uniqueDiscards = [...new Set(tileIds)].sort((left, right) => left - right)
	return uniqueDiscards.map(discardId => {
		const remaining = [...tileIds]
		remaining.splice(remaining.indexOf(discardId), 1)
		const waits = findWinningTiles(remaining, mode)
		return {
			discardId,
			waits,
			waitDetails: waits.map(tileId => ({
				tileId,
				...analyzeWinningHand([...remaining, tileId], mode)
			}))
		}
	}).filter(option => option.waits.length > 0)
}

function validateTiles(tileIds, mode = 'sichuan') {
	const counts = Array(34).fill(0)
	for (const id of tileIds) {
		if (!Number.isInteger(id) || id < 0 || id >= 34) return '存在无效麻将牌'
		if (mode === 'sichuan' && id >= 27) return '川麻只使用万、条、筒，不包含风牌和三元牌'
		if (++counts[id] > 4) return '同一种牌最多选择 4 张'
	}
	return ''
}

function toCounts(tileIds) {
	const counts = Array(34).fill(0)
	tileIds.forEach(id => counts[id]++)
	return counts
}

function hasThreeSuits(tileIds) {
	return new Set(tileIds.map(id => Math.floor(id / 9))).size === 3
}

function analyzeWinningHand(tileIds, mode = 'sichuan') {
	const counts = toCounts(tileIds)
	if (mode === 'sichuan' && hasThreeSuits(tileIds)) {
		return { win: false, message: '川麻胡牌必须缺一门，不能同时含万、条、筒三门牌' }
	}
	if (mode === 'standard' && isThirteenOrphans(counts)) {
		return {
			win: true,
			type: '十三幺',
			groups: ['十三种幺九字牌 + 任意一对幺九字牌']
		}
	}
	if (isSevenPairs(counts)) {
		const groups = []
		for (let id = 0; id < counts.length; id++) {
			for (let pair = 0; pair < counts[id] / 2; pair++) groups.push(`${tileName(id)}一对`)
		}
		const result = {
			win: true,
			structure: 'seven-pairs',
			type: '七对',
			groups
		}
		return mode === 'sichuan' ? withSichuanFan(tileIds, result) : result
	}

	const standardResults = []
	for (let pairId = 0; pairId < counts.length; pairId++) {
		if (counts[pairId] < 2) continue
		const remaining = [...counts]
		remaining[pairId] -= 2
		const melds = findMelds(remaining)
		if (melds) {
			const result = {
				win: true,
				structure: 'standard',
				type: mode === 'sichuan' ? '平胡' : '普通胡牌',
				groups: [`将牌：${tileName(pairId)}一对`, ...melds]
			}
			standardResults.push(mode === 'sichuan' ? withSichuanFan(tileIds, result) : result)
		}
	}
	if (standardResults.length) {
		return mode === 'sichuan'
			? standardResults.sort((left, right) => right.fan - left.fan)[0]
			: standardResults[0]
	}
	return { win: false, message: '当前 14 张牌不能组成胡牌牌型' }
}

function withSichuanFan(tileIds, result) {
	const counts = toCounts(tileIds)
	const pureSuit = new Set(tileIds.map(id => Math.floor(id / 9))).size === 1
	const roots = counts.filter(count => count === 4).length
	const allTriplets = result.structure === 'standard'
		&& result.groups.slice(1).every(group => group.startsWith('刻子'))
	const dragonPairs = result.structure === 'seven-pairs' && roots > 0

	let type = '平胡'
	let baseFan = 1
	const patterns = []

	if (result.structure === 'seven-pairs') {
		if (pureSuit && dragonPairs) {
			type = '清龙七对'
			baseFan = 32
			patterns.push('清一色', '龙七对')
		} else if (pureSuit) {
			type = '清七对'
			baseFan = 16
			patterns.push('清一色', '七对')
		} else if (dragonPairs) {
			type = '龙七对'
			baseFan = 8
			patterns.push('龙七对')
		} else {
			type = '七对'
			baseFan = 4
			patterns.push('七对')
		}
	} else if (pureSuit && allTriplets) {
		type = '清对'
		baseFan = 8
		patterns.push('清一色', '对对胡')
	} else if (pureSuit) {
		type = '清一色'
		baseFan = 4
		patterns.push('清一色')
	} else if (allTriplets) {
		type = '对对胡'
		baseFan = 2
		patterns.push('对对胡')
	} else {
		patterns.push('平胡')
	}

	return {
		...result,
		type,
		fan: baseFan + roots,
		baseFan,
		roots,
		patterns,
		fanText: `${baseFan + roots} 番${roots ? `（含 ${roots} 根加番）` : ''}`
	}
}

function isThirteenOrphans(counts) {
	const required = [0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33]
	if (!required.every(id => counts[id] >= 1)) return false
	if (counts.some((count, id) => !required.includes(id) && count > 0)) return false
	return required.some(id => counts[id] >= 2)
}

function isSevenPairs(counts) {
	return counts.every(count => count % 2 === 0) && counts.reduce((pairs, count) => pairs + count / 2, 0) === 7
}

function findMelds(counts) {
	const first = counts.findIndex(count => count > 0)
	if (first === -1) return []

	if (counts[first] >= 3) {
		counts[first] -= 3
		const rest = findMelds(counts)
		counts[first] += 3
		if (rest) return [`刻子：${tileName(first)} × 3`, ...rest]
	}

	const suitIndex = first % 9
	if (first < 27 && suitIndex <= 6 && counts[first + 1] > 0 && counts[first + 2] > 0) {
		counts[first]--
		counts[first + 1]--
		counts[first + 2]--
		const rest = findMelds(counts)
		counts[first]++
		counts[first + 1]++
		counts[first + 2]++
		if (rest) return [`顺子：${tileName(first)}、${tileName(first + 1)}、${tileName(first + 2)}`, ...rest]
	}
	return null
}
