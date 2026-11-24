package com.cosmiclogic.stashradar.search;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
//#if MC >= 1.19
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.phys.Vec3;
//#endif
//#if MC >= 1.19.3
import net.minecraft.network.syncher.SynchedEntityData;
//#endif

import java.util.ArrayList;
import java.util.List;

/**
 * A glowing outline that shows through walls, for one player only, on every client (no mod needed on the
 * player's side): the server tells just that player's game about an invisible, glowing shulker sitting in the
 * container's block. The shulker never exists in the world, so nobody else sees it and nothing is saved.
 * Glowing invisible shulkers draw as a block-sized outline. Because the player's game thinks something solid is
 * there, the Highlighter only shows it while the player is more than a few blocks away (so chests still open).
 */
public final class Glow {

    private Glow() {
    }

    /** One fake shulker: its entity id and the packets that make the player's game show it. */
    public static final class Ghost {
        public final int id;
        public final List<Packet<?>> spawn;

        Ghost(int id, List<Packet<?>> spawn) {
            this.id = id;
            this.spawn = spawn;
        }
    }

    /** Builds (but doesn't send) the fake glowing shulker for a block. */
    public static Ghost build(ServerLevel level, BlockPos pos) {
        Shulker shulker = new Shulker(EntityType.SHULKER, level);
        shulker.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        shulker.setInvisible(true);
        shulker.setSilent(true);
        //#if MC >= 1.17
        shulker.setGlowingTag(true);
        //#else
        shulker.setGlowing(true);
        //#endif
        List<Packet<?>> packets = new ArrayList<>();
        //#if MC >= 1.20.5
        packets.add(new ClientboundAddEntityPacket(shulker.getId(), shulker.getUUID(), shulker.getX(), shulker.getY(), shulker.getZ(),
                0f, 0f, EntityType.SHULKER, 0, Vec3.ZERO, 0d));
        //#elif MC >= 1.19
        packets.add(new ClientboundAddEntityPacket(shulker));
        //#else
        packets.add(shulker.getAddEntityPacket());
        //#endif
        //#if MC >= 1.19.3
        List<SynchedEntityData.DataValue<?>> data = shulker.getEntityData().getNonDefaultValues();
        if (data != null) {
            packets.add(new ClientboundSetEntityDataPacket(shulker.getId(), data));
        }
        //#else
        packets.add(new ClientboundSetEntityDataPacket(shulker.getId(), shulker.getEntityData(), true));
        //#endif
        return new Ghost(shulker.getId(), packets);
    }

    public static void show(ServerPlayer player, Ghost ghost) {
        for (Packet<?> p : ghost.spawn) {
            player.connection.send(p);
        }
    }

    public static void hide(ServerPlayer player, int... ids) {
        if (ids.length > 0) {
            player.connection.send(new ClientboundRemoveEntitiesPacket(ids));
        }
    }
}
