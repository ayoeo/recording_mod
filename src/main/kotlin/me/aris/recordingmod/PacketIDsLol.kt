package me.aris.recordingmod

import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.play.server.*

@Suppress("HasPlatformType")
object PacketIDsLol {
  val teamsID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketTeams()
    )
  val scoreboardID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketScoreboardObjective()
    )
  val bossBarID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketUpdateBossInfo()
    )
  val playerListID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketPlayerListItem()
    )

  val chunkDataID =
    EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketChunkData())
  val multiBlockChangeID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketMultiBlockChange()
    )
  val blockChangeID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketBlockChange()
    )
  val chunkUnloadID =
    EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketUnloadChunk())
  val spawnPlayerID =
    EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketSpawnPlayer())
  val spawnMobID =
    EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketSpawnMob())
  val spawnEXPOrbID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketSpawnExperienceOrb()
    )
  val spawnPaintingID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketSpawnPainting()
    )
  val spawnObjectID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketSpawnObject()
    )
  val spawnGlobalID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketSpawnGlobalEntity()
    )
  val destroyEntityID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketDestroyEntities()
    )

  val respawnID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketRespawn()
    )
}
