CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    openid VARCHAR(64) NOT NULL UNIQUE,
    unionid VARCHAR(64),
    nickname VARCHAR(20),
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app_user IS '小程序用户表';
COMMENT ON COLUMN app_user.id IS '系统用户主键';
COMMENT ON COLUMN app_user.openid IS '用户在当前微信小程序 AppID 下的唯一标识';
COMMENT ON COLUMN app_user.unionid IS '微信开放平台统一用户标识，可为空';
COMMENT ON COLUMN app_user.nickname IS '用户昵称，首次完善资料前可为空';
COMMENT ON COLUMN app_user.avatar_url IS '用户头像访问地址，首次完善资料前可为空';
COMMENT ON COLUMN app_user.created_at IS '用户首次进入时间';
COMMENT ON COLUMN app_user.updated_at IS '用户资料最后更新时间';

CREATE TABLE IF NOT EXISTS auth_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth_token IS '用户登录令牌表';
COMMENT ON COLUMN auth_token.id IS '令牌记录主键';
COMMENT ON COLUMN auth_token.user_id IS '令牌所属用户 ID';
COMMENT ON COLUMN auth_token.token_hash IS '登录令牌的 SHA-256 哈希值，不保存令牌明文';
COMMENT ON COLUMN auth_token.expires_at IS '令牌过期时间';
COMMENT ON COLUMN auth_token.created_at IS '令牌签发时间';

CREATE TABLE IF NOT EXISTS game_session (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT REFERENCES app_user(id),
    join_code VARCHAR(6) NOT NULL,
    invite_token VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLAYING' CHECK (status IN ('PLAYING', 'FINISHED')),
    round_count INTEGER NOT NULL DEFAULT 0 CHECK (round_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);

ALTER TABLE game_session ADD COLUMN IF NOT EXISTS join_code VARCHAR(6);
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS invite_token VARCHAR(64);
ALTER TABLE game_session DROP COLUMN IF EXISTS password_hash;
ALTER TABLE game_session DROP COLUMN IF EXISTS password_salt;
UPDATE game_session
SET invite_token = MD5(RANDOM()::TEXT || CLOCK_TIMESTAMP()::TEXT || id::TEXT)
WHERE invite_token IS NULL;
ALTER TABLE game_session ALTER COLUMN invite_token SET NOT NULL;

COMMENT ON TABLE game_session IS '麻将对局表';
COMMENT ON COLUMN game_session.id IS '对局主键';
COMMENT ON COLUMN game_session.creator_id IS '对局创建用户 ID';
COMMENT ON COLUMN game_session.join_code IS '房间展示编号';
COMMENT ON COLUMN game_session.invite_token IS '二维码加入使用的随机邀请令牌';
COMMENT ON COLUMN game_session.status IS '对局状态：PLAYING 进行中，FINISHED 已结束';
COMMENT ON COLUMN game_session.round_count IS '已完成的小局数量';
COMMENT ON COLUMN game_session.created_at IS '对局创建时间';
COMMENT ON COLUMN game_session.finished_at IS '对局结束时间，进行中时为空';

CREATE TABLE IF NOT EXISTS game_player (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES app_user(id),
    player_order INTEGER NOT NULL,
    name VARCHAR(20) NOT NULL,
    avatar_url TEXT,
    current_score INTEGER NOT NULL DEFAULT 0,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (game_id, player_order)
);

ALTER TABLE game_player ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON TABLE game_player IS '对局玩家表';
COMMENT ON COLUMN game_player.id IS '对局玩家主键';
COMMENT ON COLUMN game_player.game_id IS '所属对局 ID';
COMMENT ON COLUMN game_player.user_id IS '关联的小程序用户 ID，手工添加的牌友为空';
COMMENT ON COLUMN game_player.player_order IS '成员加入顺序，从 0 开始，不限制房间人数';
COMMENT ON COLUMN game_player.name IS '本场对局中的玩家名称快照';
COMMENT ON COLUMN game_player.avatar_url IS '本场对局中的玩家头像地址快照';
COMMENT ON COLUMN game_player.current_score IS '玩家当前累计总分，初始值为 0';
COMMENT ON COLUMN game_player.hidden IS '成员是否已退出当前房间';

CREATE TABLE IF NOT EXISTS game_round (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
    round_no INTEGER NOT NULL CHECK (round_no > 0),
    note VARCHAR(100) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (game_id, round_no)
);

COMMENT ON TABLE game_round IS '对局小局记录表';
COMMENT ON COLUMN game_round.id IS '小局主键';
COMMENT ON COLUMN game_round.game_id IS '所属对局 ID';
COMMENT ON COLUMN game_round.round_no IS '小局序号，从 1 开始';
COMMENT ON COLUMN game_round.note IS '本局备注，例如自摸或杠上花';
COMMENT ON COLUMN game_round.created_at IS '本局记录时间';

CREATE TABLE IF NOT EXISTS round_score (
    id BIGSERIAL PRIMARY KEY,
    round_id BIGINT NOT NULL REFERENCES game_round(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES game_player(id) ON DELETE CASCADE,
    score_delta INTEGER NOT NULL,
    UNIQUE (round_id, player_id)
);

COMMENT ON TABLE round_score IS '小局玩家分数明细表';
COMMENT ON COLUMN round_score.id IS '分数明细主键';
COMMENT ON COLUMN round_score.round_id IS '所属小局 ID';
COMMENT ON COLUMN round_score.player_id IS '对应的对局玩家 ID';
COMMENT ON COLUMN round_score.score_delta IS '玩家本局分数变化，可为正数、负数或零';

CREATE TABLE IF NOT EXISTS game_event (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL,
    player_id BIGINT REFERENCES game_player(id) ON DELETE SET NULL,
    player_name VARCHAR(20) NOT NULL,
    target_player_id BIGINT REFERENCES game_player(id) ON DELETE SET NULL,
    target_player_name VARCHAR(20),
    amount INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE game_event IS '房间事件明细表';
COMMENT ON COLUMN game_event.event_type IS '事件类型：JOIN、LEAVE、TRANSFER';
COMMENT ON COLUMN game_event.player_id IS '操作成员 ID';
COMMENT ON COLUMN game_event.player_name IS '操作成员名称快照';
COMMENT ON COLUMN game_event.target_player_id IS '给分接收成员 ID';
COMMENT ON COLUMN game_event.target_player_name IS '给分接收成员名称快照';
COMMENT ON COLUMN game_event.amount IS '给分数值，仅 TRANSFER 事件有值';
COMMENT ON COLUMN game_event.created_at IS '事件发生时间';

ALTER TABLE game_session DROP COLUMN IF EXISTS title;
ALTER TABLE game_session DROP COLUMN IF EXISTS initial_score;
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS creator_id BIGINT REFERENCES app_user(id);
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS join_code VARCHAR(6);
UPDATE game_session
SET join_code = LPAD((id % 1000000)::TEXT, 6, '0')
WHERE join_code IS NULL;
ALTER TABLE game_session ALTER COLUMN join_code SET NOT NULL;
ALTER TABLE game_player ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES app_user(id);
ALTER TABLE game_player ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE game_player DROP CONSTRAINT IF EXISTS game_player_player_order_check;
ALTER TABLE round_score ADD COLUMN IF NOT EXISTS id BIGSERIAL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_game_session_join_code ON game_session(join_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_game_session_invite_token ON game_session(invite_token);
CREATE UNIQUE INDEX IF NOT EXISTS uk_round_score_id ON round_score(id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_user_unionid ON app_user(unionid) WHERE unionid IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_auth_token_user ON auth_token(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_token_expires ON auth_token(expires_at);
CREATE INDEX IF NOT EXISTS idx_game_session_creator ON game_session(creator_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_game_player_user ON game_player(game_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_game_player_game ON game_player(game_id);
CREATE INDEX IF NOT EXISTS idx_game_round_game ON game_round(game_id, round_no DESC);
CREATE INDEX IF NOT EXISTS idx_round_score_round ON round_score(round_id);
CREATE INDEX IF NOT EXISTS idx_game_event_game ON game_event(game_id, created_at DESC);
