package com.vzmah.app.domain.model

sealed interface PresentationCommand {

    data object NextSlide : PresentationCommand

    data object PreviousSlide : PresentationCommand

    data object ToggleBlank : PresentationCommand

    data object StartShow : PresentationCommand

    data object StartShowFromCurrent : PresentationCommand

    data object EndShow : PresentationCommand

    data object LaserPointer : PresentationCommand

    data object PenTool : PresentationCommand

    data object ArrowTool : PresentationCommand

    data object EraseInk : PresentationCommand

    data class PointerMove(val dx: Int, val dy: Int) : PresentationCommand
}

enum class CommandOrigin {

    TOUCH,

    GESTURE,

    POINTER,

    SWIPE,
}

data class CommandRequest(
    val command: PresentationCommand,
    val origin: CommandOrigin = CommandOrigin.TOUCH,
)

val PresentationCommand.isShowStart: Boolean
    get() = this == PresentationCommand.StartShow ||
        this == PresentationCommand.StartShowFromCurrent

val PresentationCommand.isShowEnd: Boolean
    get() = this == PresentationCommand.EndShow

fun nextShowActive(current: Boolean, command: PresentationCommand): Boolean = when {
    command.isShowStart -> true
    command.isShowEnd -> false
    else -> current
}

val PresentationCommand.title: String
    get() = when (this) {
        PresentationCommand.NextSlide -> "Следующий слайд"
        PresentationCommand.PreviousSlide -> "Предыдущий слайд"
        PresentationCommand.ToggleBlank -> "Чёрный экран"
        PresentationCommand.StartShow -> "Запуск показа"
        PresentationCommand.StartShowFromCurrent -> "Запуск с текущего слайда"
        PresentationCommand.EndShow -> "Завершение показа"
        PresentationCommand.LaserPointer -> "Лазерная указка"
        PresentationCommand.PenTool -> "Перо"
        PresentationCommand.ArrowTool -> "Стрелка"
        PresentationCommand.EraseInk -> "Стереть чернила"
        is PresentationCommand.PointerMove -> "Указка"
    }
