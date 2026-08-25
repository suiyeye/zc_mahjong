package com.zc.zcapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zc.zcapi.common.exception.BusinessException;
import com.zc.zcapi.dto.GameDetailResponse;
import com.zc.zcapi.dto.GameEventResponse;
import com.zc.zcapi.dto.GameSummaryResponse;
import com.zc.zcapi.dto.PlayerResponse;
import com.zc.zcapi.dto.RoundResponse;
import com.zc.zcapi.dto.ScanJoinRequest;
import com.zc.zcapi.dto.TransferScoreRequest;
import com.zc.zcapi.entity.GameEventEntity;
import com.zc.zcapi.entity.GamePlayerEntity;
import com.zc.zcapi.entity.GameRoundEntity;
import com.zc.zcapi.entity.GameSessionEntity;
import com.zc.zcapi.entity.RoundScoreEntity;
import com.zc.zcapi.entity.UserEntity;
import com.zc.zcapi.mapper.GameEventMapper;
import com.zc.zcapi.mapper.GamePlayerMapper;
import com.zc.zcapi.mapper.GameRoundMapper;
import com.zc.zcapi.mapper.GameSessionMapper;
import com.zc.zcapi.mapper.RoundScoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GameService {

    private final GameSessionMapper gameSessionMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final GameEventMapper gameEventMapper;
    private final GameRoundMapper gameRoundMapper;
    private final RoundScoreMapper roundScoreMapper;
    private final GameSocketService gameSocketService;
    private final UserService userService;

    public GameService(GameSessionMapper gameSessionMapper, GamePlayerMapper gamePlayerMapper,
                       GameEventMapper gameEventMapper, GameRoundMapper gameRoundMapper,
                       RoundScoreMapper roundScoreMapper, GameSocketService gameSocketService,
                       UserService userService) {
        this.gameSessionMapper = gameSessionMapper;
        this.gamePlayerMapper = gamePlayerMapper;
        this.gameEventMapper = gameEventMapper;
        this.gameRoundMapper = gameRoundMapper;
        this.roundScoreMapper = roundScoreMapper;
        this.gameSocketService = gameSocketService;
        this.userService = userService;
    }

    public List<GameSummaryResponse> listGames(long userId) {
        Set<Long> joinedGameIds = gamePlayerMapper.selectList(Wrappers.<GamePlayerEntity>lambdaQuery()
                        .eq(GamePlayerEntity::getUserId, userId)
                        .eq(GamePlayerEntity::getHidden, false))
                .stream().map(GamePlayerEntity::getGameId).collect(Collectors.toSet());
        if (joinedGameIds.isEmpty()) {
            return List.of();
        }
        return gameSessionMapper.selectList(Wrappers.<GameSessionEntity>lambdaQuery()
                        .in(GameSessionEntity::getId, joinedGameIds)
                        .orderByDesc(GameSessionEntity::getCreatedAt))
                .stream().map(game -> new GameSummaryResponse(game.getId(), game.getJoinCode(),
                        game.getStatus(), game.getRoundCount(), game.getCreatedAt(), game.getFinishedAt(),
                        game.getCreatorId() == userId, players(game.getId())))
                .toList();
    }

    public GameDetailResponse getGame(long userId, long gameId) {
        GameSessionEntity game = requireAccessibleGame(userId, gameId);
        return detail(game, userId);
    }

    @Transactional
    public GameDetailResponse createGame(long userId) {
        UserEntity creator = requireCompletedUser(userId);

        GameSessionEntity game = new GameSessionEntity();
        game.setCreatorId(userId);
        game.setJoinCode(generateJoinCode());
        game.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        game.setStatus("PLAYING");
        game.setRoundCount(0);
        game.setCreatedAt(OffsetDateTime.now());
        gameSessionMapper.insert(game);

        GamePlayerEntity creatorPlayer = insertPlayer(game.getId(), creator, 0);
        insertEvent(game.getId(), "JOIN", creatorPlayer, null, null);
        gameSocketService.broadcastGameUpdatedAfterCommit(game.getId());
        return detail(game, userId);
    }

    @Transactional
    public GameDetailResponse scanJoinGame(long userId, ScanJoinRequest request) {
        if (request == null || request.inviteToken() == null || request.inviteToken().isBlank()) {
            throw new BusinessException(400, "二维码邀请信息无效");
        }
        GameSessionEntity foundGame = gameSessionMapper.selectOne(Wrappers.<GameSessionEntity>lambdaQuery()
                .eq(GameSessionEntity::getInviteToken, request.inviteToken().trim()));
        if (foundGame == null) {
            throw new BusinessException(404, "二维码对应的房间不存在");
        }
        GameSessionEntity game = gameSessionMapper.selectByIdForUpdate(foundGame.getId());
        return joinRoom(userId, game);
    }

    public String getInviteToken(long userId, long gameId) {
        return requireAccessibleGame(userId, gameId).getInviteToken();
    }

    private GameDetailResponse joinRoom(long userId, GameSessionEntity game) {
        UserEntity user = requireCompletedUser(userId);
        if (!"PLAYING".equals(game.getStatus())) {
            throw new BusinessException(400, "该房间已经结束");
        }
        GamePlayerEntity joined = findPlayer(game.getId(), userId);
        if (joined == null) {
            Integer maxOrder = gamePlayerMapper.selectList(Wrappers.<GamePlayerEntity>lambdaQuery()
                            .eq(GamePlayerEntity::getGameId, game.getId())
                            .orderByDesc(GamePlayerEntity::getPlayerOrder)
                            .last("LIMIT 1"))
                    .stream().findFirst().map(GamePlayerEntity::getPlayerOrder).orElse(-1);
            GamePlayerEntity newPlayer = insertPlayer(game.getId(), user, maxOrder + 1);
            insertEvent(game.getId(), "JOIN", newPlayer, null, null);
        } else if (Boolean.TRUE.equals(joined.getHidden())) {
            joined.setHidden(false);
            gamePlayerMapper.updateById(joined);
            insertEvent(game.getId(), "JOIN", joined, null, null);
        }
        gameSocketService.broadcastGameUpdatedAfterCommit(game.getId());
        return detail(game, userId);
    }

    @Transactional
    public GameDetailResponse transferScore(long userId, long gameId, TransferScoreRequest request) {
        GameSessionEntity game = gameSessionMapper.selectByIdForUpdate(gameId);
        GamePlayerEntity sender = findPlayer(gameId, userId);
        if (game == null || sender == null || Boolean.TRUE.equals(sender.getHidden())) {
            throw new BusinessException(404, "房间不存在");
        }
        if (!"PLAYING".equals(game.getStatus())) {
            throw new BusinessException(400, "已结束的房间不能给分");
        }
        if (request == null || request.targetPlayerId() == null || request.amount() == null
                || request.amount() <= 0 || request.amount() > 1_000_000) {
            throw new BusinessException(400, "请输入 1 至 1000000 的分数");
        }
        GamePlayerEntity target = gamePlayerMapper.selectOne(Wrappers.<GamePlayerEntity>lambdaQuery()
                .eq(GamePlayerEntity::getId, request.targetPlayerId())
                .eq(GamePlayerEntity::getGameId, gameId)
                .eq(GamePlayerEntity::getHidden, false));
        if (target == null) {
            throw new BusinessException(400, "接收成员不存在");
        }
        if (sender.getId().equals(target.getId())) {
            throw new BusinessException(400, "不能给自己分数");
        }

        int amount = request.amount();
        GameRoundEntity round = new GameRoundEntity();
        round.setGameId(gameId);
        round.setRoundNo(game.getRoundCount() + 1);
        round.setNote(sender.getName() + " → " + target.getName() + " " + amount + " 分");
        round.setCreatedAt(OffsetDateTime.now());
        gameRoundMapper.insert(round);

        insertRoundScore(round.getId(), sender.getId(), -amount);
        insertRoundScore(round.getId(), target.getId(), amount);
        changeScore(gameId, sender.getId(), -amount);
        changeScore(gameId, target.getId(), amount);
        insertEvent(gameId, "TRANSFER", sender, target, amount);

        game.setRoundCount(round.getRoundNo());
        gameSessionMapper.updateById(game);
        gameSocketService.broadcastGameUpdatedAfterCommit(gameId);
        return detail(game, userId);
    }

    @Transactional
    public void removeGameRecord(long userId, long gameId) {
        GamePlayerEntity player = findPlayer(gameId, userId);
        if (player == null) {
            throw new BusinessException(404, "房间不存在");
        }
        player.setHidden(true);
        gamePlayerMapper.updateById(player);
    }

    @Transactional
    public void leaveGame(long userId, long gameId) {
        GameSessionEntity game = requireAccessibleGame(userId, gameId);
        GamePlayerEntity player = findPlayer(gameId, userId);
        player.setHidden(true);
        gamePlayerMapper.updateById(player);
        insertEvent(gameId, "LEAVE", player, null, null);
        if (game.getCreatorId() == userId && "PLAYING".equals(game.getStatus())) {
            game.setStatus("FINISHED");
            game.setFinishedAt(OffsetDateTime.now());
            gameSessionMapper.updateById(game);
        }
        gameSocketService.disconnectUserAfterCommit(gameId, userId);
        gameSocketService.broadcastGameUpdatedAfterCommit(gameId);
    }

    @Transactional
    public GameDetailResponse restartGame(long userId, long gameId) {
        GameSessionEntity game = requireAccessibleGame(userId, gameId);
        if (!"FINISHED".equals(game.getStatus())) {
            throw new BusinessException(400, "只有已结束的房间可以继续开始");
        }
        game.setStatus("PLAYING");
        game.setFinishedAt(null);
        gameSessionMapper.updateById(game);
        gameSocketService.broadcastGameUpdatedAfterCommit(gameId);
        return detail(game, userId);
    }

    @Transactional
    public GameDetailResponse finishGame(long userId, long gameId) {
        GameSessionEntity game = requireAccessibleGame(userId, gameId);
        if ("PLAYING".equals(game.getStatus())) {
            game.setStatus("FINISHED");
            game.setFinishedAt(OffsetDateTime.now());
            gameSessionMapper.updateById(game);
        }
        gameSocketService.broadcastGameUpdatedAfterCommit(gameId);
        return detail(game, userId);
    }

    private GamePlayerEntity insertPlayer(long gameId, UserEntity user, int playerOrder) {
        GamePlayerEntity player = new GamePlayerEntity();
        player.setGameId(gameId);
        player.setUserId(user.getId());
        player.setPlayerOrder(playerOrder);
        player.setName(user.getNickname());
        player.setAvatarUrl(user.getAvatarUrl());
        player.setCurrentScore(0);
        player.setHidden(false);
        gamePlayerMapper.insert(player);
        return player;
    }

    private void insertEvent(long gameId, String eventType, GamePlayerEntity player,
                             GamePlayerEntity targetPlayer, Integer amount) {
        GameEventEntity event = new GameEventEntity();
        event.setGameId(gameId);
        event.setEventType(eventType);
        event.setPlayerId(player.getId());
        event.setPlayerName(player.getName());
        event.setTargetPlayerId(targetPlayer == null ? null : targetPlayer.getId());
        event.setTargetPlayerName(targetPlayer == null ? null : targetPlayer.getName());
        event.setAmount(amount);
        event.setCreatedAt(OffsetDateTime.now());
        gameEventMapper.insert(event);
    }

    private void insertRoundScore(long roundId, long playerId, int delta) {
        RoundScoreEntity score = new RoundScoreEntity();
        score.setRoundId(roundId);
        score.setPlayerId(playerId);
        score.setScoreDelta(delta);
        roundScoreMapper.insert(score);
    }

    private void changeScore(long gameId, long playerId, int delta) {
        gamePlayerMapper.update(null, Wrappers.<GamePlayerEntity>lambdaUpdate()
                .eq(GamePlayerEntity::getId, playerId)
                .eq(GamePlayerEntity::getGameId, gameId)
                .setSql("current_score = current_score + " + delta));
    }

    private UserEntity requireCompletedUser(long userId) {
        UserEntity user = userService.findById(userId);
        if (user.getNickname() == null || user.getNickname().isBlank()
                || user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            throw new BusinessException(400, "请先完善头像和昵称");
        }
        return user;
    }

    private GamePlayerEntity findPlayer(long gameId, long userId) {
        return gamePlayerMapper.selectOne(Wrappers.<GamePlayerEntity>lambdaQuery()
                .eq(GamePlayerEntity::getGameId, gameId)
                .eq(GamePlayerEntity::getUserId, userId));
    }

    public boolean canAccessGame(long userId, long gameId) {
        GameSessionEntity game = gameSessionMapper.selectById(gameId);
        GamePlayerEntity player = findPlayer(gameId, userId);
        return game != null && player != null && !Boolean.TRUE.equals(player.getHidden());
    }

    private GameSessionEntity requireAccessibleGame(long userId, long gameId) {
        GameSessionEntity game = gameSessionMapper.selectById(gameId);
        GamePlayerEntity player = findPlayer(gameId, userId);
        if (game == null || player == null || Boolean.TRUE.equals(player.getHidden())) {
            throw new BusinessException(404, "房间不存在");
        }
        return game;
    }

    private GameSessionEntity requireOwnedGame(long userId, long gameId) {
        GameSessionEntity game = requireAccessibleGame(userId, gameId);
        if (game.getCreatorId() != userId) {
            throw new BusinessException(403, "只有房主可以结束房间");
        }
        return game;
    }

    private GameDetailResponse detail(GameSessionEntity game, long userId) {
        GamePlayerEntity currentPlayer = findPlayer(game.getId(), userId);
        return new GameDetailResponse(game.getId(), game.getJoinCode(), game.getStatus(),
                game.getRoundCount(), game.getCreatedAt(), game.getFinishedAt(),
                game.getCreatorId() == userId, currentPlayer == null ? null : currentPlayer.getId(),
                players(game.getId()), rounds(game.getId()), events(game.getId()));
    }

    private List<GameEventResponse> events(long gameId) {
        List<GameEventEntity> events = gameEventMapper.selectList(Wrappers.<GameEventEntity>lambdaQuery()
                .eq(GameEventEntity::getGameId, gameId)
                .orderByAsc(GameEventEntity::getCreatedAt)
                .orderByAsc(GameEventEntity::getId));
        Map<Long, Integer> runningScore = new HashMap<>();
        Map<Long, long[]> transferScoreAfter = new HashMap<>();
        for (GameEventEntity event : events) {
            if (!"TRANSFER".equals(event.getEventType()) || event.getTargetPlayerId() == null) {
                continue;
            }
            int senderNext = runningScore.merge(event.getPlayerId(), -event.getAmount(), Integer::sum);
            int targetNext = runningScore.merge(event.getTargetPlayerId(), event.getAmount(), Integer::sum);
            transferScoreAfter.put(event.getId(), new long[]{senderNext, targetNext});
        }
        Collections.reverse(events);
        return events.stream().map(event -> {
            long[] after = transferScoreAfter.get(event.getId());
            Integer senderAfter = after == null ? null : (int) after[0];
            Integer targetAfter = after == null ? null : (int) after[1];
            Integer senderBefore = senderAfter == null ? null : senderAfter + event.getAmount();
            Integer targetBefore = targetAfter == null ? null : targetAfter - event.getAmount();
            return new GameEventResponse(event.getId(), event.getEventType(),
                    event.getPlayerId(), event.getPlayerName(), event.getTargetPlayerId(),
                    event.getTargetPlayerName(), event.getAmount(), senderAfter, targetAfter,
                    senderBefore, targetBefore, event.getCreatedAt());
        }).toList();
    }

    private String generateJoinCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (!gameSessionMapper.exists(Wrappers.<GameSessionEntity>lambdaQuery()
                    .eq(GameSessionEntity::getJoinCode, code))) {
                return code;
            }
        }
        throw new BusinessException(500, "生成房间号失败，请重试");
    }

    private List<PlayerResponse> players(long gameId) {
        return gamePlayerMapper.selectList(Wrappers.<GamePlayerEntity>lambdaQuery()
                        .eq(GamePlayerEntity::getGameId, gameId)
                        .eq(GamePlayerEntity::getHidden, false)
                        .orderByAsc(GamePlayerEntity::getPlayerOrder))
                .stream().map(this::toPlayerResponse)
                .toList();
    }

    private PlayerResponse toPlayerResponse(GamePlayerEntity player) {
        return new PlayerResponse(player.getId(), player.getUserId(), player.getPlayerOrder(),
                player.getName(), player.getAvatarUrl(), player.getCurrentScore());
    }

    private List<RoundResponse> rounds(long gameId) {
        Map<Long, PlayerResponse> playerMap = gamePlayerMapper.selectList(Wrappers.<GamePlayerEntity>lambdaQuery()
                        .eq(GamePlayerEntity::getGameId, gameId))
                .stream().map(this::toPlayerResponse)
                .collect(Collectors.toMap(PlayerResponse::id, Function.identity()));
        Map<Long, Integer> runningScore = new HashMap<>();
        List<RoundResponse> responses = new ArrayList<>();
        for (GameRoundEntity round : gameRoundMapper.selectList(Wrappers.<GameRoundEntity>lambdaQuery()
                .eq(GameRoundEntity::getGameId, gameId)
                .orderByAsc(GameRoundEntity::getRoundNo))) {
            List<RoundResponse.RoundScoreResponse> scores = roundScoreMapper.selectList(
                            Wrappers.<RoundScoreEntity>lambdaQuery()
                                    .eq(RoundScoreEntity::getRoundId, round.getId()))
                    .stream().map(score -> {
                        long playerId = score.getPlayerId();
                        int next = runningScore.merge(playerId, score.getScoreDelta(), Integer::sum);
                        return new RoundResponse.RoundScoreResponse(playerId,
                                playerMap.get(playerId).name(), score.getScoreDelta(), next);
                    }).toList();
            responses.add(new RoundResponse(round.getId(), round.getRoundNo(), round.getNote(),
                    round.getCreatedAt(), scores));
        }
        Collections.reverse(responses);
        return responses;
    }
}
