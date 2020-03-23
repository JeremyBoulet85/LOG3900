CREATE OR REPLACE FUNCTION LOG3900.getAccountNamesByUsername(in_username VARCHAR(20))
    RETURNS TABLE  (out_firstName VARCHAR(20), out_lastName VARCHAR(20)) AS $$
    BEGIN
        RETURN QUERY
        SELECT log3900.account.firstname as out_firstname, log3900.account.lastname as out_lastname
        FROM LOG3900.Account
        WHERE log3900.account.username = in_username;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.getAccountConnectionsByUsername(in_username VARCHAR(20))
    RETURNS TABLE  (out_isLogin BOOLEAN, out_times VARCHAR(20)) AS $$
    BEGIN
        RETURN QUERY
        SELECT log3900.connection.is_login as out_isLogin, log3900.connection.times as out_times
        FROM LOG3900.connection full outer JOIN log3900.account ON log3900.account.id = log3900.connection.account_id
        WHERE log3900.account.username = in_username;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.registerAccount(in_username VARCHAR(20), in_password VARCHAR(100), in_firstName VARCHAR(100), in_lastName VARCHAR(100)) RETURNS void AS $$
    DECLARE
        account_id INT;

    BEGIN
       IF EXISTS( SELECT A.username FROM LOG3900.Account as A WHERE A.username = in_username) THEN
            RAISE EXCEPTION 'Username exist already.';
       END IF;
       INSERT INTO LOG3900.Account VALUES(DEFAULT, in_username, in_password, in_firstName, in_lastName, DEFAULT)RETURNING id INTO account_id;
       INSERT INTO LOG3900.accountchannel VALUES(account_id, 'general');
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.loginAccount(in_username VARCHAR(20), in_password VARCHAR(100)) RETURNS void AS $$
    BEGIN
        IF NOT EXISTS( SELECT A.username FROM LOG3900.Account as A WHERE A.username = in_username) THEN
            RAISE EXCEPTION 'Username is incorrect.';
        END IF;
        IF NOT EXISTS( SELECT A.hashPwd FROM LOG3900.Account as A WHERE A.hashPwd = in_password) THEN
            RAISE EXCEPTION 'Password is incorrect.';
        END IF;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.logConnection(in_username VARCHAR(20), is_login BOOLEAN, in_times VARCHAR(30)) RETURNS void AS $$
    DECLARE
    the_id INT;
    BEGIN
        SELECT a.id
        FROM LOG3900.account AS a
        WHERE a.username = in_username
        INTO the_id;

        IF the_id IS NULL THEN
            INSERT INTO LOG3900.connection VALUES(null, is_login, in_times);
        ELSE
            INSERT INTO LOG3900.connection VALUES(the_id, is_login, in_times);
        END IF;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.getMessagesWithChannelId(in_id VARCHAR(20))
RETURNS TABLE (out_username VARCHAR(20), out_content TEXT, out_times VARCHAR(8)) AS $$
    BEGIN
        RETURN QUERY
        WITH RECURSIVE messageOrder(parent_id, id, content, level, ts, account_id)
        AS (
            SELECT parent_id, id, content, 0, ts, account_id
            FROM LOG3900.MESSAGES AS messages
            WHERE parent_id IS NULL
            AND channel_id = in_id

            UNION ALL

            SELECT messages.parent_id, messages.id, messages.content, messageOrder.level+1, messages.ts, messages.account_id
            FROM LOG3900.MESSAGES AS messages
            JOIN messageOrder ON (messages.parent_id = messageOrder.id)
        )
        SELECT a.username, content, ts
        FROM messageOrder, LOG3900.account AS a
        WHERE account_id = a.id
        ORDER BY level;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.joinChannel(in_account_un VARCHAR(20), in_channel_id VARCHAR(20)) RETURNS INTEGER AS $$
    DECLARE
        channel_id VARCHAR(20);
        account_id INTEGER;
        is_new     INTEGER;
    BEGIN
        SELECT id FROM log3900.Channel WHERE id = in_channel_id INTO channel_id;
        SELECT id FROM log3900.account WHERE username = in_account_un INTO account_id;

        is_new = 0;

        IF channel_id IS NOT NULL THEN
            INSERT INTO LOG3900.accountchannel VALUES(account_id, channel_id);
        ELSE
            is_new = 1;
            INSERT INTO log3900.channel VALUES(in_channel_id, DEFAULT);
            INSERT INTO LOG3900.accountchannel VALUES(account_id, in_channel_id);
        END IF;

        RETURN is_new;
    END;
$$LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION LOG3900.leaveChannel(in_account_un VARCHAR(20), in_channel_id VARCHAR(20)) RETURNS VOID AS $$
    DECLARE
        delete_id INTEGER;
    BEGIN
        SELECT account.id FROM log3900.account WHERE username = in_account_un INTO delete_id;

        DELETE FROM LOG3900.accountChannel as acc
        WHERE acc.account_id = delete_id
        AND acc.channel_id = in_channel_id;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.insertChannelMessage(in_channel_id VARCHAR(20), in_account_id INT, in_content TEXT, in_ts VARCHAR(8)) RETURNS VOID AS $$
    DECLARE
        last_id INT;
    BEGIN
        SELECT m.id
        FROM LOG3900.messages as m
        WHERE m.channel_id = in_channel_id
        ORDER BY m.id DESC
        LIMIT 1
        INTO last_id;

        IF last_id IS NULL THEN
            INSERT INTO LOG3900.messages VALUES(DEFAULT, null, in_ts, in_content, in_channel_id, in_account_id);
        ELSE
            INSERT INTO LOG3900.messages VALUES(DEFAULT, last_id, in_ts, in_content, in_channel_id, in_account_id);
        END IF;
    END;
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.getSearChannelsByName(in_username VARCHAR(20), in_word TEXT)
RETURNS TABLE (out_channel VARCHAR(20), sub TEXT) AS $$
    BEGIN
        RETURN QUERY
        SELECT a.channel_id, 'true' sub
        FROM log3900.account as acc, log3900.accountchannel as a
        WHERE acc.id = a.account_id
        AND acc.username = in_username
        AND a.channel_id LIKE in_word

        UNION ALL

        SELECT id, 'false' sub
        FROM log3900.channel
        WHERE id NOT IN (
            SELECT channel_id
            FROM log3900.account as acc, log3900.accountchannel as a
            WHERE acc.id = a.account_id
            AND acc.username = in_username)
        AND id LIKE in_word;
    END
$$LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION LOG3900.registerGame(in_gamemode VARCHAR(20), in_date VARCHAR(30), in_elapsedTime INT, in_winner VARCHAR(20), in_userPts json) RETURNS VOID AS $$
    DECLARE
        winner_id INT;
        game_id INT;
        i json;
        user_id_loop INT;
    BEGIN
        SELECT account.id FROM log3900.account WHERE username = in_winner INTO winner_id;

        INSERT INTO Log3900.game VALUES(DEFAULt, in_gamemode, in_date, in_elapsedTime, winner_id) RETURNING game.id INTO game_id;

        FOR i IN SELECT * FROM json_array_elements(in_userPts)
        LOOP
            SELECT account.id FROM log3900.account WHERE username = i->>'username' INTO user_id_loop;
            INSERT INTO Log3900.accountgame VALUES(user_id_loop, game_id, CAST(i->>'point' AS INT));
        END LOOP;
    END
$$LANGUAGE plpgsql;