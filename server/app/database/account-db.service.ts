import { injectable } from "inversify";
import { DatabaseService } from "../database/database";
import { IRegistration, ILogin } from "../interfaces/communication";
import * as pg from "pg";

@injectable()
export class AccountDbService extends DatabaseService {

    public async registerAccount(registration: IRegistration): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT LOG3900.registerAccount(
                                    CAST('${registration.username}' AS VARCHAR),
                                    CAST('${registration.password}' AS VARCHAR));`);
    }

    public async loginAccount(login: ILogin): Promise<pg.QueryResult> {
        return this.pool.query(`SELECT LOG3900.loginAccount(
                                    CAST('${login.username}' AS VARCHAR),
                                    CAST('${login.password}' AS VARCHAR));`);
    }

} 