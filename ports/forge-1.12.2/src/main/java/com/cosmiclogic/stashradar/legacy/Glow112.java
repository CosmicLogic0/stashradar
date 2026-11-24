package com.cosmiclogic.stashradar.legacy;

import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketSpawnMob;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Glowing outline through walls for 1.12.2, seen only by the searching player and needing no mod on their
 * game: the server tells just that player's game about an invisible, glowing shulker in the container's block.
 * It never exists in the world. Same idea as the other versions' Glow class.
 */
final class Glow112 {

    private Glow112() {
    }

    static final class Ghost {
        final int id;
        final List<Packet<?>> spawn;

        Ghost(int id, List<Packet<?>> spawn) {
            this.id = id;
            this.spawn = spawn;
        }
    }

    static Ghost build(World world, BlockPos pos) {
        EntityShulker shulker = new EntityShulker(world);
        shulker.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        shulker.setInvisible(true);
        shulker.setSilent(true);
        shulker.setGlowing(true);
        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new SPacketSpawnMob(shulker));
        packets.add(new SPacketEntityMetadata(shulker.getEntityId(), shulker.getDataManager(), true));
        return new Ghost(shulker.getEntityId(), packets);
    }

    static void show(EntityPlayerMP player, Ghost ghost) {
        for (Packet<?> p : ghost.spawn) {
            player.connection.sendPacket(p);
        }
    }

    static void hide(EntityPlayerMP player, int... ids) {
        if (ids.length > 0) {
            player.connection.sendPacket(new SPacketDestroyEntities(ids));
        }
    }
}
