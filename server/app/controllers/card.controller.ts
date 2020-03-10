import { NextFunction, Request, Response, Router } from 'express';
import { inject, injectable } from 'inversify';
import { GameCardService } from '../services/game/game-card.service';

import Types from '../types';

@injectable()
export class CardController {
    public router: Router;

    public constructor(@inject(Types.GameCardService) private cardServ: GameCardService) {
        this.configureRouter();
    }

    private configureRouter() {
        this.router = Router();

        this.router.get('/', async (req: Request, res: Response, next: NextFunction) => {
            res.json(await this.cardServ.getGameCards());
        });

        this.router.delete('/delete/:gameID', async (req: Request, res: Response, next: NextFunction) => {
            await this.cardServ.deleteCard(req.params.gameID)
            res.status(200).send();
        });

    }
}