import core.ATMMachineV2;
import services.FileATMStateService;
import services.PrinterService;
import users.TechnicianV2Panel;
import model.Account;
import java.util.Scanner;

// MainV1 is the entry point for the ATM V2 console application
// OOP: Uses encapsulated ATM, printer, and persistence services
// SOLID - SRP: MainV1 handles only user interface and menu flow
public class MainV1 {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // 1. Create the persistence service first
        FileATMStateService stateService = new FileATMStateService();

        // 2. Load the ACTUAL saved levels from the JSON file
        int savedPaper = stateService.loadPaperLevel();
        int savedInk = stateService.loadInkLevel();

        // 3. Inject the SAVED levels into the printer
        PrinterService printer = new PrinterService(savedPaper, savedInk);

        // 4. Finally, inject the service and the printer into the ATM
        ATMMachineV2 atm = new ATMMachineV2(stateService, printer);

        while (true) {
            // ------------------- HOME SCREEN -------------------
            System.out.println("\n--- ATM HOME SCREEN ---");
            System.out.println("1. Customer Login");
            System.out.println("2. Technician Login");
            System.out.println("3. Exit");
            System.out.print("Select: ");

            int choice;
            try {
                choice = Integer.parseInt(sc.nextLine());
            } catch (Exception e) {
                System.out.println("Invalid input.");
                continue;
            }

            switch(choice) {
                // ---------------- CUSTOMER LOGIN ----------------
                case 1 -> {
                    if (atm.isOutOfService()) {
                        System.out.println("ATM out of service. Please try again later.");
                        break;
                    }

                    System.out.print("Name: ");
                    String name = sc.nextLine();
                    System.out.print("PIN: ");
                    String pin = sc.nextLine();

                    var user = atm.authenticateUser(name, pin); // Authentication + potential auto-registration
                    if (user != null) userMenu(atm, sc, user); // Launch user menu
                }

                // ---------------- TECHNICIAN LOGIN ----------------
                case 2 -> {
                    System.out.print("ID: ");
                    String id = sc.nextLine();
                    System.out.print("Pass: ");
                    String pass = sc.nextLine();

                    if (atm.authenticateTech(id, pass))
                        new TechnicianV2Panel(atm).run(); // Opens technician panel
                    else
                        System.out.println("Access Denied.");
                }

                // ---------------- EXIT ----------------
                case 3 -> {
                    System.out.println("Goodbye!");
                    sc.close();
                    return;
                }

                default -> System.out.println("Invalid option.");
            }
        }
    }

    // ------------------- USER MENU -------------------
    private static void userMenu(ATMMachineV2 atm, Scanner sc, Account user) {
        boolean loggedIn = true;

        while(loggedIn) {
            System.out.println("\n--- USER MENU (" + user.getOwner() + ") ---");
            System.out.println("1. Check Balance");
            System.out.println("2. Deposit");
            System.out.println("3. Withdraw");
            System.out.println("4. Logout");
            System.out.println("5. Transaction History");
            System.out.print("Action: ");

            int act;
            try {
                act = Integer.parseInt(sc.nextLine());
            } catch(Exception e) {
                System.out.println("Invalid input.");
                continue;
            }

            switch(act) {
                case 1 -> System.out.println("Balance: €" + String.format("%.2f", atm.checkBalance(user.getOwner())));

                case 2 -> {
                    if (!depositMenu(atm, sc, user)) {
                        System.out.println("[!] ATM out of service. Returning to home.");
                        loggedIn = false;
                    }
                }

                case 3 -> {
                    if (!withdrawMenu(atm, sc, user)) {
                        System.out.println("[!] ATM out of service. Returning to home.");
                        loggedIn = false;
                    }
                }

                case 4 -> {
                    atm.logout();
                    loggedIn = false;
                }

                case 5 -> {
                    if (user.getTransactions().isEmpty()) {
                        System.out.println("No transactions yet.");
                    } else {
                        System.out.println("\n--- Transaction History ---");
                        user.getTransactions().forEach(System.out::println);
                    }
                }

                default -> System.out.println("Invalid option.");
            } // switch
        } // while
    } // userMenu

    // ------------------- DEPOSIT MENU -------------------
    private static boolean depositMenu(ATMMachineV2 atm, Scanner sc, Account user) {
        if(!atm.checkPaperInkWarning(sc)) return true;

        System.out.print("Desired total deposit (€): ");
        int desired;
        try { desired = Integer.parseInt(sc.nextLine()); }
        catch (Exception e) { System.out.println("Invalid amount."); return true; }

        if (desired <= 0 || desired % 5 != 0) {
            System.out.println("Amount must be positive and in multiples of €5.");
            return true;
        }

        int[] denoms = {5,10,20,50,100};
        int sum = 0;

        while (sum < desired) {
            int remaining = desired - sum;
            System.out.println("Choose a denomination to add (remaining: €" + remaining + "):");
            for (int i = 0; i < denoms.length; i++) System.out.println((i+1) + ". €" + denoms[i]);
            System.out.println("0. Cancel deposit");
            System.out.print("Select: ");

            int sel;
            try { sel = Integer.parseInt(sc.nextLine()); } catch(Exception e){ System.out.println("Invalid."); continue; }
            if (sel == 0) { System.out.println("Deposit cancelled."); return true; }
            if (sel < 1 || sel > denoms.length) { System.out.println("Invalid selection."); continue; }

            int chosen = denoms[sel-1];
            int maxQty = remaining / chosen;
            if (maxQty == 0) { System.out.println("[!] Cannot add €" + chosen + " note, exceeds remaining."); continue; }

            System.out.print("How many €" + chosen + " notes? (max " + maxQty + "): ");
            int qty;
            try { qty = Integer.parseInt(sc.nextLine()); } catch(Exception e){ System.out.println("Invalid number."); continue; }
            if (qty < 1 || qty > maxQty) { System.out.println("Enter a number between 1 and " + maxQty); continue; }

            int add = chosen * qty;
            sum += add;
            System.out.println("Added €" + add + " (" + qty + "x€" + chosen + ") (total: €" + sum + ")");
        }

        atm.deposit(user.getOwner(), sum);
        user.addTransaction("Deposit", sum);
        atm.printReceipt();

        if (atm.isOutOfService()) {
            System.out.println("[!] ATM out of service. Logging out...");
            atm.logout();
            return false;
        }
        return true;
    }

    // ------------------- WITHDRAW MENU -------------------
    private static boolean withdrawMenu(ATMMachineV2 atm, Scanner sc, Account user) {
        if(!atm.checkPaperInkWarning(sc)) return true;

        System.out.print("Desired total withdrawal (€): ");
        int desired;
        try { desired = Integer.parseInt(sc.nextLine()); } catch (Exception e) { System.out.println("Invalid amount."); return true; }

        if (desired <= 0 || desired % 5 != 0) {
            System.out.println("Amount must be positive and in multiples of €5.");
            return true;
        }

        double balance = atm.checkBalance(user.getOwner());
        if (desired > balance) { System.out.println("[!] Insufficient account balance."); return true; }
        if (desired > atm.getCashAvailable()) { System.out.println("[!] ATM does not have enough cash."); return true; }

        int[] denoms = {5,10,20,50,100};
        int sum = 0;

        while (sum < desired) {
            int remaining = desired - sum;
            System.out.println("Choose a denomination to withdraw (remaining: €" + remaining + "):");
            for (int i = 0; i < denoms.length; i++) System.out.println((i+1) + ". €" + denoms[i]);
            System.out.println("0. Cancel withdrawal");
            System.out.print("Select: ");

            int sel;
            try { sel = Integer.parseInt(sc.nextLine()); } catch(Exception e){ System.out.println("Invalid."); continue; }
            if (sel == 0) { System.out.println("Withdrawal cancelled."); return true; }
            if (sel < 1 || sel > denoms.length) { System.out.println("Invalid selection."); continue; }

            int chosen = denoms[sel-1];
            int maxByRemaining = remaining / chosen;
            int maxByATM = (int)((atm.getCashAvailable() - sum) / chosen);
            int maxQty = Math.min(maxByRemaining, Math.max(0, maxByATM));

            if (maxQty == 0) { System.out.println("[!] Cannot add €" + chosen + " note; exceeds remaining or ATM lacks cash."); continue; }

            System.out.print("How many €" + chosen + " notes? (max " + maxQty + "): ");
            int qty;
            try { qty = Integer.parseInt(sc.nextLine()); } catch(Exception e){ System.out.println("Invalid number."); continue; }
            if (qty < 1 || qty > maxQty) { System.out.println("Enter a number between 1 and " + maxQty); continue; }

            int add = chosen * qty;
            sum += add;
            System.out.println("Added €" + add + " (" + qty + "x€" + chosen + ") (total: €" + sum + ")");
        }

        if(!atm.withdraw(user.getOwner(), sum)) return true;
        user.addTransaction("Withdraw", sum);

        if (atm.isOutOfService()) {
            System.out.println("[!] ATM out of service. Logging out...");
            atm.logout();
            return false;
        }
        return true;
    }
}
