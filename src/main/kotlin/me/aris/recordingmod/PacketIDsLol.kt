package me.aris.recordingmod

import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.play.server.*
import net.minecraft.network.status.server.SPacketServerInfo

@Suppress("HasPlatformType")
object PacketIDsLol {
  val sleepID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketUseBed()
    )
  val teamsID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketTeams()
    )

  val scoreboardObjective =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketScoreboardObjective()
    )

  val displayObjective =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketDisplayObjective()
    )

  val updateScore =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketUpdateScore()
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

  val headerFooterID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketPlayerListHeaderFooter()
    )
  
//  val headerFooterID =
//    EnumConnectionState.PLAY.getPacketId(
//      EnumPacketDirection.CLIENTBOUND,
//      SPacketSpawnPlayer()
//    )

  val chunkDataID =
    EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketChunkData())

  val explosionID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketExplosion()
    )

  val multiBlockChangeID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketMultiBlockChange()
    )
  val joinGameID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketJoinGame()
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

  val entityMetadata =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketEntityMetadata()
    )

  val entityEquipment =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketEntityEquipment()
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

  val soundID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketSoundEffect()
    )

  val chatID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketChat()
    )

  val customSoundID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketCustomSound()
    )

  val effectID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketEffect()

    )

  val entityEffectID =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketEntityEffect()
    )

  val setslotid =
    EnumConnectionState.PLAY.getPacketId(
      EnumPacketDirection.CLIENTBOUND,
      SPacketSetSlot()
    )
}
