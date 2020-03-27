import { Arena } from "./arena";
import { IUser } from "../../interfaces/user-manager";
import { IGameplayChat, IGameplayDraw, IGameplayAnnouncement, ICorrAns, IGameplayReady, GameMode, IGameplayEraser, IDrawing } from "../../interfaces/game";
import { IGameRule } from "../../interfaces/rule";
import { GameManagerService } from "./game-manager.service";
import { DrawingTools } from "./utils/drawing-tools";

import * as io from 'socket.io';

const format = require('string-format');

const ANNOUNCEMENT = "{0} has found the answer";
const ONE_SEC = 1000;
const TOTAL_TIME = 30000;
const INIT_DRAW_PTS = 20;

export class ArenaFfa extends Arena {

    private drawPtr: number;
    private curArenaInterval: NodeJS.Timeout;

    private curTime: number;
    private userWithCorrectAns: ICorrAns[];

    private isEveryoneHasRightAnswer: boolean;

    private isBotDrawing: boolean;

    public constructor(type: GameMode, arenaId: number, users: IUser[], room: string, io: io.Server, rules: IGameRule[], gm: GameManagerService) {
        super(type, arenaId, users, room, io, rules, gm)
        
        this.drawPtr = 0;
        this.userWithCorrectAns = [];
        this.isEveryoneHasRightAnswer = false;
        this.isBotDrawing = false;
    }

    public start(): void {
        console.log("[Debug] Starting arena FFA", this.room);
           
        try {
            this.checkArenaLoadingState(() => this.startSubGame());
        }  catch(e) {
            this.end();
        }
    }

    private startSubGame(): void {
        
        if (this.resetSubGame()) {
            this.end();
            return;
        }

        if (this.isBotDrawing)
            this.startBotDrawing(this.users[this.drawPtr - 1].username, TOTAL_TIME);

        let timer = 0;
        this.curArenaInterval = setInterval(() => {
            console.log("[Debug] Timer is: ", timer);
            
            this.socketServer.to(this.room).emit("game-timer", {time: (TOTAL_TIME - timer)/ONE_SEC});
            this.curTime = timer;

            if (timer >= TOTAL_TIME || this.isEveryoneHasRightAnswer) {
                clearInterval(this.curArenaInterval);

                this.handlePoints()
                
                if (this.drawPtr >= this.users.length) {
                    // Handle end of game
                    this.end()
                } else {
                    // Make next person to draw
                    this.startSubGame();
                }
            }
            timer += ONE_SEC;

        }, ONE_SEC);
    }

    private resetSubGame(): void | boolean {
        this.userWithCorrectAns = [];
        this.isEveryoneHasRightAnswer = false;
        this.isBotDrawing = false;
        
        this.chooseRandomRule();
        if (this.attributeRoles())
            return true;
    }

    public receiveInfo(socket: io.Socket, mes: IGameplayChat | IGameplayDraw | IGameplayReady | IGameplayEraser): void {
        if (this.isDraw(mes))
            this.handleGameplayDraw(socket, mes);
        else if (this.isChat(mes))
            this.handleGameplayChat(mes);
        else 
            this.handleGameplayReady(mes);
    }


    private handleGameplayDraw(socket: io.Socket, mes: IGameplayDraw | IGameplayEraser): void {
        this.users.forEach(u => {
            if (u.username != mes.username)
                socket.to(this.room).emit("draw", this.mapToDrawing(mes))
        });
    }

    protected handleGameplayChat(mes: IGameplayChat): void {
        if (this.isRightAnswer(mes.content)) {
            this.userWithCorrectAns.push({
                username: mes.username,
                time: (TOTAL_TIME - this.curTime)/ONE_SEC,
                ratio: 1 - this.calculateRatio()});

            const encAnswer = this.encryptAnswer(mes.content);
            this.sendToChat({username: mes.username, content: encAnswer, isServer: false});
            this.sendToChat({
                username: "Server",
                content: format(ANNOUNCEMENT, mes.username),
                isServer: true});

            if (this.checkIfEveryoneHasRightAnswer())
                this.isEveryoneHasRightAnswer = true;
        } else {
            this.sendToChat({username: mes.username, content: mes.content, isServer: false});
        }
    }

    protected startBotDrawing(botName: string, arenaTime: number): NodeJS.Timeout {
        
        const drawings: IDrawing[] = DrawingTools.prepareGameRule(this.curRule.drawing);
        const bot = this.initBot(botName, drawings);

        return bot.draw(this.socketServer, this.room, arenaTime);
    }

    protected handlePoints(): void {
        // Give points to guesser
        // pts = time_left * (1 - ratio_found)
        this.userWithCorrectAns.forEach(u => {
            const pts = this.userMapPoints.get(u.username) as number;
            const newPts = Math.floor(u.time * u.ratio);
            this.userMapPoints.set(u.username, pts + newPts);
        });

        // Give points to drawer
        // pts = 20 * ratio_found
        const drawName = this.users[this.drawPtr - 1].username;
        const drawPts = this.userMapPoints.get(drawName) as number;
        const drawNewPts = Math.floor(INIT_DRAW_PTS * this.calculateRatio());
        this.userMapPoints.set(drawName, drawNewPts + drawPts);
    }

    private attributeRoles(): void | boolean {
        let user = this.users[this.drawPtr++];

        while (this.isUserDc(user.username)) {

            // if player is disconnect, increment drawer pointer
            if (this.drawPtr++ >= this.users.length) {
                clearInterval(this.curArenaInterval);
                return true
            }
            user = this.users[this.drawPtr];
        }
        this.updateDrawerRole(user);
    }
    
    private updateDrawerRole(drawer: IUser): void {
        this.users.forEach(u => {
            if (!this.isUserDc(u.username) && u.username !== drawer.username)
                this.socketServer.to(u.socketId).emit("game-drawer", {username: drawer.username});
        });
        
        if (this.isBot(drawer.username))
            this.isBotDrawing = true;
        else
            this.socketServer.to(drawer.socketId).emit("game-drawer", {username: drawer.username, object: this.curRule.solution});    
    }

    private sendToChat(obj: IGameplayAnnouncement): void {
        this.socketServer.to(this.room).emit("game-chat", obj);
    }

    private isUserDc(username: string): boolean {
        return this.dcPlayer.includes(username);
    }

    private calculateRatio(): number {
        return Math.floor(this.userWithCorrectAns.length / this.users.length);
    }

    private checkIfEveryoneHasRightAnswer(): boolean {
        return (this.users.length - this.dcPlayer.length) === this.userWithCorrectAns.length;
    }

}