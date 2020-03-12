import { injectable, inject } from "inversify";
import { IGameplayDraw, IDrawing } from "./interfaces/game";
import { GameManagerService } from "./services/game/game-manager.service";
import Types from './types';

@injectable()
export class Bot {

    private username: string;
    private image: IDrawing[];
    private nextStroke: number;
    private hint: string;

    constructor(image: IDrawing[],
        username: string = "BOT:bob",
        hint: string = "no hint for you!",
        @inject(Types.GameManagerService) private gameManager: GameManagerService) {

        this.username = username;
        this.image = image;
        this.hint = hint;
        this.findStroke();
    }

    public sendNextStroke(): boolean {
        const out: any = this.image[this.nextStroke];           // whacky stuff here
        out.username = this.username;                           // whacky stuff here
        const res: IGameplayDraw = out;                         // whacky stuff here
        this.gameManager.sendMessageToArena(res);               // ceci ou la fonction qui enverra a tout le monde
        this.findStroke();
        return !this.isDone();   // retourne true si il reste des traits a ajouter.
    }

    private isDone(): boolean {
        return (this.nextStroke == this.image.length);
    }

    private findStroke(): void { // quand le dernier trait a ete envoyee, on met le prochain a image.length;
        if (this.nextStroke == null) {
            this.nextStroke = 0;
            return;
        }
        this.nextStroke++;
    }

    public getHint(): string {
        return this.hint;
    }

}
