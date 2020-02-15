import * as chai from "chai";
import * as spies from "chai-spies";
import Types from '../types';
import { container } from "../inversify.config";
import { ChatService } from "../services/chat.service";
import { IUser } from "../interfaces/user-manager";
import * as io from 'socket.io';

chai.use(spies);

describe("ChatService", () => {

    let service: ChatService;

    beforeEach(() => {
        container.snapshot();
        service = container.get<ChatService>(Types.ChatService);
    });

    afterEach(() => {
        container.restore();
    });

    it("Should add user to channelMap correctly when channel has not been added once", async () => {
        //given
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "general"}]}
        });

        //when
        await service.addUserToChannelMap({username: "username", socketId: "socketId"});
        
        //then
        const list = service["channelMapUsersList"].get("general") as IUser[];
        chai.expect(list.length).to.be.equal(1);
    });

    it("Should add user to channelMap correctly when channel has been added once", async () => {
        //given
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "general"}]}
        });

        //when
        await service.addUserToChannelMap({username: "username", socketId: "socketId"});
        await service.addUserToChannelMap({username: "username1", socketId: "socketId1"});
        
        //then
        const list = service["channelMapUsersList"].get("general") as IUser[];
        chai.expect(list.length).to.be.equal(2);
    });

    it("Should remove user correctly from channel map", async () => {
        //given
        service["channelMapUsersList"].set("general", [{username: "name", socketId: "yomama"}]);

        //when
        service.removeUserFromChannelMap("name");
                
        //then
        chai.expect(service["channelMapUsersList"].get("general")).to.be.equal(undefined);
    });

    it("Should not remove all user from channel map", async () => {
        //given
        service["channelMapUsersList"].set("general", [{username: "name1", socketId: "yomama"}]);
        service["channelMapUsersList"].set("general", [{username: "name2", socketId: "yomama"}]);

        //when
        service.removeUserFromChannelMap("name1");
                
        //then
        const list = service["channelMapUsersList"].get("general");
        chai.expect(list).to.be.deep.equal([{username: "name2", socketId: "yomama"}]);
    });

    it("Should format time correctly", async () => {
        //given
        //when
        const time = service["convertDateTemplate"]();
                
        //then
        chai.expect(time.length).to.be.equal(8);
    });

    it("Should send to invitee invite notification", async () => {
        //given
        service["usernameMapSocketId"].set("invitee", "socketId")
        chai.spy.on(service, "joinChannel", () => { return {status: 200, message:"success"}});
        const spy = chai.spy.on(service["socket"], "to");

        //when
        const result = await service.sendInviteToChannel("inviter", "invitee", "channel");

        //then
        chai.expect(result.message).to.be.equal("Invitation sent successfully");
        chai.expect(spy).to.have.been.called();
    });

});

describe("ChatService, Join/Leave", () => {

    let service: ChatService;

    beforeEach(() => {
        container.snapshot();
        service = container.get<ChatService>(Types.ChatService);
    });

    afterEach(() => {
        container.restore();
    });

    it("Should join channel succesfully", async () => {
        //given
        chai.spy.on(service["db"], "joinChannel", () => {});
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "notEqualChannel"}]}
        });

        //when
        const result = await service.joinChannel("username", "channel");

        //then
        chai.expect(result.status).to.be.equal(200);
        chai.expect(result.message).to.be.equal("Successfully joined channel");
    });

    it("Should return already subscribe message when joining with existing channel", async () => {
        //given
        chai.spy.on(service["db"], "joinChannel", () => {});
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "channel"}]}
        });

        //when
        const result = await service.joinChannel("username", "channel");

        //then
        chai.expect(result.status).to.be.equal(400);
        chai.expect(result.message).to.be.equal("username is already subscribed to channel.");
    });

    it("Should leave channel successfully", async () => {
        //given
        chai.spy.on(service["db"], "leaveChannel", () => {});
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "channel"}]}
        });

        //when
        const result = await service.leaveChannel("username", "channel");

        //then
        chai.expect(result.status).to.be.equal(200);
        chai.expect(result.message).to.be.equal("Successfully left channel");
    });

    it("Should return is not subscribed to channel when user is not sub to channel", async () => {
        //given
        chai.spy.on(service["db"], "leaveChannel", () => {});
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "notChannel"}]}
        });

        //when
        const result = await service.leaveChannel("username", "channel");

        //then
        chai.expect(result.status).to.be.equal(400);
        chai.expect(result.message).to.be.equal("username is not subscribed to channel.");
    });

    it("Should return cannot leave default channel when leaving general", async () => {
        //given
        chai.spy.on(service["db"], "leaveChannel", () => {});
        chai.spy.on(service["db"], "getChannelsWithAccountName", () => {
            return {rows:[{channel_id: "general"}]}
        });

        //when
        const result = await service.leaveChannel("username", "general");

        //then
        chai.expect(result.status).to.be.equal(400);
        chai.expect(result.message).to.be.equal("cannot leave default channel: general.");
    });
});