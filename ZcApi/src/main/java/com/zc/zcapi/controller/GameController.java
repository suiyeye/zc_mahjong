package com.zc.zcapi.controller;

import com.zc.zcapi.common.CurrentUserHolder;
import com.zc.zcapi.dto.GameDetailResponse;
import com.zc.zcapi.dto.GameSummaryResponse;
import com.zc.zcapi.dto.RoomInviteResponse;
import com.zc.zcapi.dto.ScanJoinRequest;
import com.zc.zcapi.dto.TransferScoreRequest;
import com.zc.zcapi.service.GameService;
import com.zc.zcapi.service.QrCodeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final QrCodeService qrCodeService;

    public GameController(GameService gameService, QrCodeService qrCodeService) {
        this.gameService = gameService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public List<GameSummaryResponse> listGames() {
        return gameService.listGames(CurrentUserHolder.require().id());
    }

    @GetMapping("/{gameId}")
    public GameDetailResponse getGame(@PathVariable long gameId) {
        return gameService.getGame(CurrentUserHolder.require().id(), gameId);
    }

    @PostMapping
    public GameDetailResponse createGame() {
        return gameService.createGame(CurrentUserHolder.require().id());
    }

    @PostMapping("/scan-join")
    public GameDetailResponse scanJoinGame(@RequestBody ScanJoinRequest request) {
        return gameService.scanJoinGame(CurrentUserHolder.require().id(), request);
    }

    @GetMapping("/{gameId}/invite")
    public RoomInviteResponse getRoomInvite(@PathVariable long gameId) {
        String token = gameService.getInviteToken(CurrentUserHolder.require().id(), gameId);
        return qrCodeService.createRoomInvite(token);
    }

    @PostMapping("/{gameId}/transfers")
    public GameDetailResponse transferScore(@PathVariable long gameId,
                                            @RequestBody TransferScoreRequest request) {
        return gameService.transferScore(CurrentUserHolder.require().id(), gameId, request);
    }

    @DeleteMapping("/{gameId}")
    public void removeGameRecord(@PathVariable long gameId) {
        gameService.removeGameRecord(CurrentUserHolder.require().id(), gameId);
    }

    @PostMapping("/{gameId}/leave")
    public void leaveGame(@PathVariable long gameId) {
        gameService.leaveGame(CurrentUserHolder.require().id(), gameId);
    }

    @PostMapping("/{gameId}/restart")
    public GameDetailResponse restartGame(@PathVariable long gameId) {
        return gameService.restartGame(CurrentUserHolder.require().id(), gameId);
    }

    @PostMapping("/{gameId}/finish")
    public GameDetailResponse finishGame(@PathVariable long gameId) {
        return gameService.finishGame(CurrentUserHolder.require().id(), gameId);
    }
}
