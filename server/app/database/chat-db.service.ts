import { injectable } from "inversify";
import { DatabaseService } from "../database/database";
import { IChannelMessageDB } from '../interfaces/chat'; 
import * as pg from "pg";

@injectable()
export class ChatDbService extends DatabaseService {

    public async getAccountIdByUsername(username: string): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT a.id
                                FROM log3900.Account as a
                                WHERE  a.username = '${username}';`);
    }

    public async getMessagesWithChannelId(id: string): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT (LOG3900.getMessagesWithChannelId(
                                    CAST('${id}' AS VARCHAR))).*;`);
    }

    public async createChannelWithAccountId(id: number): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT LOG3900.createChannelWithAccountId(
                                    CAST('${id}' AS INTEGER));`);
    }

    public async insertChannelMessage(mes: IChannelMessageDB): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT LOG3900.insertChannelMessage(
                                    CAST('${mes.channel_id}' AS VARCHAR),
                                    CAST('${mes.account_id}' AS INTEGER),
                                    CAST('${mes.content}'    AS TEXT),
                                    CAST('${mes.time}'       AS VARCHAR));`);
    }

    public async getChannelsWithAccountName(username: string): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT DISTINCT a.channel_id
                                FROM log3900.account as acc, log3900.accountchannel as a
                                WHERE acc.id = a.account_id
                                AND acc.username = '${username}';`);
    }
} 