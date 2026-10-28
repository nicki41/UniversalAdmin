package dev.universaladmin.modules.nick;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The rules for nick names, and made-up names for {@code /nick random}.
 * A nick is a valid Minecraft name, so clients, tab lists and other
 * plugins treat it like any other player's.
 */
public final class NickNames {

    public static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** Words of {@code /admin nick} that are not names. */
    static final Set<String> KEYWORDS = Set.of("random", "off", "list", "own");

    private static final List<String> FIRST = List.of(
            "Swift", "Quiet", "Lucky", "Brave", "Clever", "Fuzzy", "Mighty", "Silent", "Sunny", "Wild",
            "Cosmic", "Pixel", "Rusty", "Frosty", "Shadow", "Golden", "Crimson", "Misty", "Stormy", "Happy",
            "Sleepy", "Jolly", "Nimble", "Bold", "Calm", "Witty", "Lazy", "Sneaky", "Mossy", "Dusty");
    private static final List<String> SECOND = List.of(
            "Fox", "Wolf", "Otter", "Panda", "Falcon", "Badger", "Raven", "Tiger", "Koala", "Lynx",
            "Moose", "Gecko", "Bison", "Heron", "Owl", "Shark", "Yak", "Llama", "Penguin", "Turtle",
            "Beaver", "Ferret", "Hawk", "Mole", "Newt", "Pike", "Robin", "Seal", "Viper", "Wren");
    private static final int ATTEMPTS = 50;

    private NickNames() {
    }

    public static boolean valid(String name) {
        return name != null && PATTERN.matcher(name).matches() && !KEYWORDS.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * A name for {@code /nick random}: one of {@code pool}, or made up when
     * the pool is empty - never one {@code taken} refuses. Empty if every
     * name tried is taken.
     */
    public static Optional<String> random(List<String> pool, Predicate<String> taken, RandomGenerator random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String name = pool.isEmpty() ? madeUp(random) : pool.get(random.nextInt(pool.size()));
            if (valid(name) && !taken.test(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static String madeUp(RandomGenerator random) {
        String name = FIRST.get(random.nextInt(FIRST.size())) + SECOND.get(random.nextInt(SECOND.size()));
        // Two digits make a free name likely; the longest word pair still fits 16 characters.
        return random.nextBoolean() ? name + random.nextInt(10, 100) : name;
    }
}
