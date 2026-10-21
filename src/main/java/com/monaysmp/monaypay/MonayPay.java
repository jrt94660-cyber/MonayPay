package com.monaysmp.monaypay;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MonayPay extends JavaPlugin implements TabExecutor {

    private static final Pattern USERNAME =
            Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private Economy economy;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault was not found. MonayPay is disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (provider == null) {
            getLogger().severe(
                    "No Vault economy provider was found. Ensure ExcellentEconomy is enabled and hooked into Vault."
            );
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        economy = provider.getProvider();

        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(this);
            getCommand("pay").setTabCompleter(this);
        }

        getLogger().info(
                "MonayPay enabled and hooked into Vault economy: "
                        + economy.getName()
        );
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("monaypay.use")) {
            player.sendMessage(Component.text(
                    "You don't have permission to use /pay.",
                    NamedTextColor.RED
            ));
            return true;
        }

        if (args.length == 0) {
            openPaymentForm(player);
            return true;
        }

        if (args.length == 2) {
            openConfirmation(player, args[0], args[1]);
            return true;
        }

        player.sendMessage(Component.text(
                "Usage: /pay or /pay <player> <amount>",
                NamedTextColor.YELLOW
        ));
        return true;
    }

    private void openPaymentForm(Player sender) {
        List<DialogInput> inputs = List.of(
                DialogInput.text(
                        "recipient",
                        Component.text("Recipient username")
                ).initial("").maxLength(16).width(300).build(),

                DialogInput.text(
                        "amount",
                        Component.text("Amount to send (e.g. 100, 10k, 5m)")
                ).initial("").maxLength(20).width(300).build()
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(
                                Component.text(
                                        "MONAYSMP • PAY",
                                        NamedTextColor.AQUA
                                )
                        )
                        .body(List.of(
                                DialogBody.plainMessage(
                                        Component.text(
                                                "Enter a player and the amount you want to send.",
                                                NamedTextColor.GRAY
                                        )
                                )
                        ))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(
                                        Component.text(
                                                "Continue",
                                                NamedTextColor.GREEN
                                        )
                                )
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (!(audience instanceof Player player)) {
                                                return;
                                            }

                                            String recipient =
                                                    view.getText("recipient");
                                            String amount =
                                                    view.getText("amount");

                                            Bukkit.getScheduler().runTask(
                                                    this,
                                                    () -> openConfirmation(
                                                            player,
                                                            recipient,
                                                            amount
                                                    )
                                            );
                                        },
                                        ClickCallback.Options.builder()
                                                .uses(1)
                                                .build()
                                ))
                                .build(),

                        ActionButton.builder(
                                Component.text(
                                        "Cancel",
                                        NamedTextColor.RED
                                )
                        ).build()
                )));

        sender.showDialog(dialog);
    }

    private BigDecimal parseAmount(String rawAmount) {
        if (rawAmount == null) return null;
        String clean = rawAmount.trim().toLowerCase(Locale.ROOT);

        double multiplier = 1.0;
        if (clean.endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            clean = clean.substring(0, clean.length() - 1);
        }

        BigDecimal baseValue = new BigDecimal(clean);
        return baseValue.multiply(BigDecimal.valueOf(multiplier));
    }

    private void openConfirmation(
            Player sender,
            String rawRecipient,
            String rawAmount
    ) {
        String name = rawRecipient == null
                ? ""
                : rawRecipient.trim();

        if (!USERNAME.matcher(name).matches()) {
            sender.sendMessage(Component.text(
                    "Enter a valid username (3–16 letters, numbers, or underscores).",
                    NamedTextColor.RED
            ));
            return;
        }

        BigDecimal amount;

        try {
            amount = parseAmount(rawAmount).setScale(2, RoundingMode.UNNECESSARY);
        } catch (Exception ex) {
            sender.sendMessage(Component.text(
                    "Enter a valid amount (e.g. 100, 10k, 5m).",
                    NamedTextColor.RED
            ));
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.precision() > 15) {
            sender.sendMessage(Component.text(
                    "The payment amount must be greater than zero and within the supported limit.",
                    NamedTextColor.RED
            ));
            return;
        }

        if (name.equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(Component.text(
                    "You cannot pay yourself.",
                    NamedTextColor.RED
            ));
            return;
        }

        Player online = Bukkit.getPlayerExact(name);

        if (online == null) {
            sender.sendMessage(Component.text(
                    "That player must be online to receive a payment.",
                    NamedTextColor.RED
            ));
            return;
        }

        double value = amount.doubleValue();

        if (!economy.has(sender, value)) {
            sender.sendMessage(Component.text(
                    "You don't have enough money. Balance: "
                            + economy.format(economy.getBalance(sender)),
                    NamedTextColor.RED
            ));
            return;
        }

        UUID senderId = sender.getUniqueId();
        UUID recipientId = online.getUniqueId();
        String recipientName = online.getName();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(
                                Component.text(
                                        "CONFIRM PAYMENT",
                                        NamedTextColor.AQUA
                                )
                        )
                        .body(List.of(
                                DialogBody.plainMessage(
                                        Component.text(
                                                "Send "
                                                        + economy.format(value)
                                                        + " to "
                                                        + recipientName
                                                        + "?",
                                                NamedTextColor.WHITE
                                        )
                                ),
                                DialogBody.plainMessage(
                                        Component.text(
                                                "Your balance will be checked again before the transfer.",
                                                NamedTextColor.GRAY
                                        )
                                )
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(
                                        Component.text(
                                                "Confirm payment",
                                                NamedTextColor.GREEN
                                        )
                                )
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (!(audience instanceof Player clicker)
                                                    || !clicker.getUniqueId()
                                                    .equals(senderId)) {
                                                return;
                                            }

                                            Bukkit.getScheduler().runTask(
                                                    this,
                                                    () -> executePayment(
                                                            senderId,
                                                            recipientId,
                                                            recipientName,
                                                            value
                                                    )
                                            );
                                        },
                                        ClickCallback.Options.builder()
                                                .uses(1)
                                                .build()
                                ))
                                .build(),

                        ActionButton.builder(
                                Component.text(
                                        "Cancel",
                                        NamedTextColor.RED
                                )
                        ).build()
                )));

        sender.showDialog(dialog);
    }

    private void executePayment(
            UUID senderId,
            UUID recipientId,
            String recipientName,
            double amount
    ) {
        Player sender = Bukkit.getPlayer(senderId);
        Player recipient = Bukkit.getPlayer(recipientId);

        if (sender == null || !sender.isOnline()) {
            return;
        }

        if (recipient == null || !recipient.isOnline()) {
            sender.sendMessage(Component.text(
                    "Payment cancelled: the recipient is no longer online.",
                    NamedTextColor.RED
            ));
            return;
        }

        if (!Double.isFinite(amount)
                || amount <= 0
                || !economy.has(sender, amount)) {
            sender.sendMessage(Component.text(
                    "Payment failed: your balance changed or is insufficient.",
                    NamedTextColor.RED
            ));
            return;
        }

        var withdrawal = economy.withdrawPlayer(sender, amount);

        if (!withdrawal.transactionSuccess()) {
            sender.sendMessage(Component.text(
                    "Payment failed: your account could not be charged.",
                    NamedTextColor.RED
            ));
            return;
        }

        var deposit = economy.depositPlayer(recipient, amount);

        if (!deposit.transactionSuccess()) {
            var refund = economy.depositPlayer(sender, amount);

            sender.sendMessage(Component.text(
                    refund.transactionSuccess()
                            ? "Payment failed; your money was returned."
                            : "URGENT: payment failed and automatic refund failed. Contact server staff.",
                    NamedTextColor.RED
            ));

            getLogger().severe(
                    "Deposit failed for " + recipientName
                            + " after withdrawal from " + sender.getName()
                            + "; refund success="
                            + refund.transactionSuccess()
            );
            return;
        }

        sender.sendMessage(Component.text(
                "Sent " + economy.format(amount)
                        + " to " + recipient.getName() + ".",
                NamedTextColor.GREEN
        ));

        recipient.sendMessage(Component.text(
                "Received " + economy.format(amount)
                        + " from " + sender.getName() + ".",
                NamedTextColor.GREEN
        ));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(sender)
                        && player.getName()
                        .toLowerCase(Locale.ROOT)
                        .startsWith(prefix)) {
                    names.add(player.getName());
                }
            }

            return names;
        }

        return List.of();
    }
}
