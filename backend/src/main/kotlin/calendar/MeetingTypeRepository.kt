package calendar

import java.util.UUID

data class MeetingType(
    val id: UUID,
    val title: String,
    val description: String?,
    val durationMinutes: Int,
)

interface MeetingTypeRepository {
    /** All meeting types in creation order. */
    fun list(): List<MeetingType>

    fun find(id: UUID): MeetingType?

    fun add(
        title: String,
        description: String?,
        durationMinutes: Int,
    ): MeetingType
}

/** In-memory storage; data is lost on restart. */
class InMemoryMeetingTypeRepository : MeetingTypeRepository {
    private val items = mutableListOf<MeetingType>()

    @Synchronized
    override fun list(): List<MeetingType> = items.toList()

    @Synchronized
    override fun find(id: UUID): MeetingType? = items.firstOrNull { it.id == id }

    @Synchronized
    override fun add(
        title: String,
        description: String?,
        durationMinutes: Int,
    ): MeetingType {
        val meetingType = MeetingType(UUID.randomUUID(), title, description, durationMinutes)
        items += meetingType
        return meetingType
    }

    companion object {
        /** The meeting types every fresh start offers: 15 and 30 minutes. */
        fun withInitialTypes(): InMemoryMeetingTypeRepository =
            InMemoryMeetingTypeRepository().apply {
                add("Знакомство", "Короткий разговор, чтобы познакомиться и обсудить задачу.", 15)
                add("Консультация", "Подробный разбор вашего вопроса с рекомендациями.", 30)
            }
    }
}
