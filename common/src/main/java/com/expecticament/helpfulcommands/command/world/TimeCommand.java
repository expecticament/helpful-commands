package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.ServerLevelUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.dimension.DimensionType;

public class TimeCommand extends HelpfulCommandsCommand {
    private final int time;
    private final ModPermissions.Permission permission;

    private static final DynamicCommandExceptionType ERROR_NO_DEFAULT_CLOCK = new DynamicCommandExceptionType(
            (dimension) -> Component.translatableEscape("commands.time.no_default_clock", dimension)
    );

    public TimeCommand(ModCommandManager.ModCommand modCommand, int time, ModPermissions.Permission permission) {
        super(modCommand);
        this.time = time;
        this.permission = permission;
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::execute)
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, permission);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        MinecraftServer server = src.getServer();

        ServerClockManager clockManager = src.getServer().clockManager();
        ServerLevel level = src.getLevel();
        Holder<WorldClock> clock = getDefaultClock(level.dimensionTypeRegistration());

        long currentTotal = clockManager.getInstance(clock).totalTicks();
        long currentTimeOfDay = currentTotal % 24000L;
        long ticksToAdd = time - currentTimeOfDay;
        if (ticksToAdd <= 0) {
            ticksToAdd += 24000L;
        }

        clockManager.setTotalTicks(clock, currentTotal + ticksToAdd);
        server.forceGameTimeSynchronization();

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder hoverComponentBuilder = new ComponentBuilder(src);
        hoverComponentBuilder.appendTranslatable("commands.helpfulcommands.time.ticks", Component.literal(String.valueOf(clockManager.getInstance(clock).totalTicks())));
        ComponentBuilder timeComponentBuilder = new ComponentBuilder(src);
        timeComponentBuilder.appendTranslatable("commands.helpfulcommands.time." + time).setStyle(textStyles.getPrimary().withHoverEvent(new HoverEvent.ShowText(hoverComponentBuilder.build())));

        ComponentBuilder messageComponentBuilder = new ComponentBuilder(src);
        messageComponentBuilder.appendTranslatable("commands.helpfulcommands.time", Component.literal(ServerLevelUtil.getLevelLocation(level)).setStyle(textStyles.getPrimary()), timeComponentBuilder.build()).setStyle(textStyles.getSuccess());

        src.sendSuccess(messageComponentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private static Holder<WorldClock> getDefaultClock(Holder<DimensionType> dimensionType) throws CommandSyntaxException {
        return dimensionType.value().defaultClock().orElseThrow(() -> ERROR_NO_DEFAULT_CLOCK.create(dimensionType.getRegisteredName()));
    }
}
