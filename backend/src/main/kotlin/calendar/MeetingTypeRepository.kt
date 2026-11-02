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

/** Storage that test mode can wipe back to its initial content. */
interface Resettable {
    fun reset()
}

/** In-memory storage; data is lost on restart. */
class InMemoryMeetingTypeRepository :
    MeetingTypeRepository,
    Resettable {
    private val items = mutableListOf<MeetingType>()

    /** Drops everything and restores the seeded types: 15 and 30 minutes. */
    @Synchronized
    override fun reset() {
        items.clear()
        add("Знакомство", "Короткий разговор, чтобы познакомиться и обсудить задачу.", 15)
        add("Консультация", "Подробный разбор вашего вопроса с рекомендациями.", 30)
    }

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
        fun withInitialTypes(): InMemoryMeetingTypeRepository = InMemoryMeetingTypeRepository().apply { reset() }
    }
}
