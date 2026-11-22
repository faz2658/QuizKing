/**
 * The daily puzzles. Each type walks its own list one entry per day (see pick() in logic.ts), so adding entries
 * here simply makes the cycle longer. Written for QuizCadia; the riddles are traditional ones.
 */

/** A Clue Trail puzzle: clues revealed one at a time, the answer and what else counts as right. */
export interface ClueTrail {
  answer: string;
  /** Other spellings or names that count as right. */
  aliases: string[];
  category: string;
  /** Hardest first; the last one nearly gives it away. */
  clues: string[];
}

/** A Letter Vault puzzle: a word to guess in a few tries, with a hint at its category. */
export interface LetterVault {
  word: string;
  category: string;
}

/** One group of a Four by Four puzzle. */
export interface FourGroup {
  label: string;
  items: string[];
}

/** A Four by Four puzzle: sixteen items to sort into four groups. */
export interface FourByFour {
  /** Easiest group first. Each item belongs to exactly one group, and there is only one way to solve it. */
  groups: FourGroup[];
}

/** A Brain Teaser: a riddle with options and why the answer is right. */
export interface BrainTeaser {
  question: string;
  options: string[];
  /** Index into options. */
  answer: number;
  why: string;
}

/** Every Clue Trail; each day takes the next one. */
export const CLUE_TRAILS: ClueTrail[] = [
  { answer: 'Marie Curie', aliases: ['Curie', 'Madame Curie', 'Marie Sklodowska Curie'], category: 'A person', clues: [
    'Born in Warsaw in 1867.',
    'Discovered two chemical elements, one of them named after her homeland.',
    'The first person to win Nobel Prizes in two different sciences.',
    'Studied radioactivity together with her husband, Pierre.',
    'Physicist and chemist often called "Madame ___".'] },
  { answer: 'Mount Everest', aliases: ['Everest', 'Sagarmatha', 'Chomolungma'], category: 'A place', clues: [
    'Its height was first worked out by the Great Trigonometrical Survey of India.',
    'Known as Sagarmatha in Nepal and Chomolungma in Tibet.',
    'First climbed to the top in 1953.',
    'Edmund Hillary and Tenzing Norgay are famous for it.',
    'The highest mountain on Earth above sea level.'] },
  { answer: 'Titanic', aliases: ['RMS Titanic'], category: 'A thing', clues: [
    'Built in Belfast by Harland and Wolff.',
    'Left Southampton in April 1912.',
    'Newspapers called it practically unsinkable.',
    'Struck an iceberg in the North Atlantic on its first voyage.',
    'A 1997 James Cameron film shares its name.'] },
  { answer: 'Leonardo da Vinci', aliases: ['Leonardo', 'Da Vinci'], category: 'A person', clues: [
    'Born in 1452 near the Tuscan town that became part of his name.',
    'Sketched flying machines centuries before anyone flew.',
    'Filled notebooks with mirror writing.',
    'Painted The Last Supper.',
    'Painted the Mona Lisa.'] },
  { answer: 'Jupiter', aliases: [], category: 'A thing in space', clues: [
    'One of its moons is bigger than the planet Mercury.',
    'Its Great Red Spot is a storm wider than Earth.',
    'Named after the king of the Roman gods.',
    'The fifth planet from the Sun.',
    'The largest planet in our solar system.'] },
  { answer: 'Albert Einstein', aliases: ['Einstein'], category: 'A person', clues: [
    'Worked as a clerk in a patent office in Bern.',
    'Published four groundbreaking papers in 1905.',
    'Won the 1921 Nobel Prize in Physics for explaining the photoelectric effect.',
    'Developed the theory of relativity.',
    'Famous for the equation E = mc².'] },
  { answer: 'Amazon River', aliases: ['Amazon', 'The Amazon'], category: 'A place', clues: [
    'Pours more water into the sea than any other river.',
    'Named by a Spanish explorer after warrior women from Greek myth.',
    'Flows through Peru, Colombia and Brazil.',
    'Home to piranhas and pink river dolphins.',
    'The world\'s largest rainforest shares its name.'] },
  { answer: 'William Shakespeare', aliases: ['Shakespeare'], category: 'A person', clues: [
    'Baptised in Stratford-upon-Avon in 1564.',
    'Part-owner of the Globe Theatre in London.',
    'Credited with bringing many new words and phrases into English.',
    'Wrote Macbeth and Hamlet.',
    'Wrote "To be, or not to be".'] },
  { answer: 'Great Pyramid of Giza', aliases: ['Great Pyramid', 'Pyramid of Giza', 'Pyramids of Giza', 'Pyramid of Khufu'], category: 'A place', clues: [
    'Was the tallest human-made structure for more than 3,800 years.',
    'Built for the pharaoh Khufu.',
    'The only Wonder of the Ancient World still standing.',
    'Stands at the edge of Cairo.',
    'The biggest of the ancient Egyptian pyramids.'] },
  { answer: 'Penguin', aliases: ['Penguins'], category: 'An animal', clues: [
    'The largest species can dive more than 500 metres deep.',
    'Almost all live in the Southern Hemisphere.',
    'Its wings work like flippers.',
    'The emperor species raises its chicks through the Antarctic winter.',
    'A black-and-white bird that cannot fly.'] },
  { answer: 'Wolfgang Amadeus Mozart', aliases: ['Mozart', 'Amadeus Mozart'], category: 'A person', clues: [
    'Born in Salzburg in 1756.',
    'Toured the courts of Europe as a child prodigy with his sister.',
    'Wrote more than 600 works and died at 35.',
    'Composed The Magic Flute and Don Giovanni.',
    'Wolfgang Amadeus ___.'] },
  { answer: 'Eiffel Tower', aliases: ['Eiffel', 'Tour Eiffel'], category: 'A place', clues: [
    'Was the tallest structure in the world until 1930.',
    'Built for the 1889 World\'s Fair.',
    'Repainted roughly every seven years.',
    'Named after the engineer whose company built it.',
    'The iron landmark of Paris.'] },
  { answer: 'Honey', aliases: [], category: 'A food', clues: [
    'Archaeologists have found jars of it thousands of years old in Egyptian tombs, still edible.',
    'Its sweetness comes mostly from fructose and glucose.',
    'Made from flower nectar.',
    'Stored in hexagonal wax cells.',
    'Made by bees.'] },
  { answer: 'Neil Armstrong', aliases: ['Armstrong'], category: 'A person', clues: [
    'Flew the X-15 rocket plane as a test pilot.',
    'Commanded Gemini 8, the first docking of two spacecraft in orbit.',
    'Commanded Apollo 11.',
    'Said "That\'s one small step for man".',
    'The first person to walk on the Moon.'] },
  { answer: 'Australia', aliases: [], category: 'A place', clues: [
    'Its capital, Canberra, was purpose-built as a compromise between two rival cities.',
    'Home to the Great Barrier Reef.',
    'Most of its people live near the coast; the dry middle is called the Outback.',
    'Kangaroos and koalas live in the wild here.',
    'The only country that is also a whole continent.'] },
  { answer: 'Chess', aliases: [], category: 'A game', clues: [
    'Grew out of an Indian game called chaturanga.',
    'Its modern rules settled in southern Europe around 1500.',
    'A pawn that reaches the far side can be promoted.',
    'Played on 64 squares.',
    'A game ends with checkmate.'] },
  { answer: 'Frida Kahlo', aliases: ['Kahlo', 'Frida'], category: 'A person', clues: [
    'Lived in the Blue House (Casa Azul) in Coyoacán.',
    'Married the muralist Diego Rivera.',
    'A bus accident at 18 left her in pain for the rest of her life.',
    'Painted dozens of self-portraits.',
    'A Mexican painter famous for her bold eyebrows and flowers in her hair.'] },
  { answer: 'The Moon', aliases: ['Moon', 'Luna'], category: 'A thing in space', clues: [
    'Drifts about 3.8 cm further from Earth every year.',
    'Always shows the same face to Earth.',
    'Twelve people have walked on it.',
    'Its pull causes most of the ocean tides.',
    'Earth\'s only natural satellite.'] },
  { answer: 'Olympic Games', aliases: ['Olympics', 'The Olympics'], category: 'An event', clues: [
    'Revived in modern form by Pierre de Coubertin.',
    'The first modern edition was held in Athens in 1896.',
    'Its symbol is five interlocking rings.',
    'Winners receive gold, silver and bronze medals.',
    'Summer and winter editions, each held every four years.'] },
  { answer: 'Coffee', aliases: [], category: 'A drink', clues: [
    'A legend credits an Ethiopian goat herder named Kaldi with discovering it.',
    'The fruit of its plant is called a cherry.',
    'Arabica and Robusta are its two main species.',
    'Espresso, latte and cappuccino are all made from it.',
    'A hot drink brewed from roasted beans.'] },
];

/** Every Letter Vault word; each day takes the next one. */
export const LETTER_VAULTS: LetterVault[] = [
  { word: 'PLANET', category: 'Space' }, { word: 'VOLCANO', category: 'Geography' }, { word: 'VIOLIN', category: 'Music' },
  { word: 'PHARAOH', category: 'History' }, { word: 'OXYGEN', category: 'Science' }, { word: 'JAGUAR', category: 'Animals' },
  { word: 'SAFFRON', category: 'Food' }, { word: 'SONNET', category: 'Literature' }, { word: 'ROCKET', category: 'Inventions' },
  { word: 'GALAXY', category: 'Space' }, { word: 'GLACIER', category: 'Geography' }, { word: 'GUITAR', category: 'Music' },
  { word: 'KNIGHT', category: 'History' }, { word: 'MAGNET', category: 'Science' }, { word: 'DOLPHIN', category: 'Animals' },
  { word: 'MANGO', category: 'Food' }, { word: 'NOVEL', category: 'Literature' }, { word: 'CAMERA', category: 'Inventions' },
  { word: 'NEBULA', category: 'Space' }, { word: 'CANYON', category: 'Geography' }, { word: 'OPERA', category: 'Music' },
  { word: 'VIKING', category: 'History' }, { word: 'PROTON', category: 'Science' }, { word: 'FALCON', category: 'Animals' },
  { word: 'PASTA', category: 'Food' }, { word: 'POETRY', category: 'Literature' }, { word: 'COMET', category: 'Space' },
  { word: 'ISLAND', category: 'Geography' }, { word: 'TEMPO', category: 'Music' }, { word: 'EMPIRE', category: 'History' },
  { word: 'FOSSIL', category: 'Science' }, { word: 'PENGUIN', category: 'Animals' }, { word: 'COMPASS', category: 'Inventions' },
];

/** Every Four by Four; each day takes the next one. */
export const FOUR_BY_FOURS: FourByFour[] = [
  { groups: [
    { label: 'Planets', items: ['MARS', 'VENUS', 'SATURN', 'NEPTUNE'] },
    { label: 'Greek letters', items: ['ALPHA', 'DELTA', 'SIGMA', 'OMEGA'] },
    { label: 'Chess pieces', items: ['KING', 'QUEEN', 'BISHOP', 'ROOK'] },
    { label: 'Shades of blue', items: ['NAVY', 'AZURE', 'COBALT', 'CYAN'] }] },
  { groups: [
    { label: 'Rivers', items: ['NILE', 'AMAZON', 'DANUBE', 'THAMES'] },
    { label: 'Gemstones', items: ['EMERALD', 'SAPPHIRE', 'TOPAZ', 'OPAL'] },
    { label: 'Snakes', items: ['COBRA', 'VIPER', 'MAMBA', 'ADDER'] },
    { label: 'Programming languages', items: ['PYTHON', 'JAVA', 'RUBY', 'SWIFT'] }] },
  { groups: [
    { label: 'Oceans', items: ['PACIFIC', 'ATLANTIC', 'INDIAN', 'ARCTIC'] },
    { label: 'Bones', items: ['FEMUR', 'TIBIA', 'SKULL', 'RIB'] },
    { label: 'Instruments', items: ['VIOLIN', 'FLUTE', 'HARP', 'TUBA'] },
    { label: 'Card games', items: ['POKER', 'BRIDGE', 'RUMMY', 'SNAP'] }] },
  { groups: [
    { label: 'Coffee drinks', items: ['LATTE', 'MOCHA', 'ESPRESSO', 'CORTADO'] },
    { label: 'Greek gods', items: ['ZEUS', 'HERA', 'ARES', 'APOLLO'] },
    { label: 'Metals', items: ['IRON', 'COPPER', 'TIN', 'ZINC'] },
    { label: 'NASA missions', items: ['GEMINI', 'MERCURY', 'SKYLAB', 'VIKING'] }] },
  { groups: [
    { label: 'Units of time', items: ['SECOND', 'MINUTE', 'DECADE', 'CENTURY'] },
    { label: 'Shakespeare plays', items: ['HAMLET', 'OTHELLO', 'MACBETH', 'TEMPEST'] },
    { label: 'Sauces', items: ['PESTO', 'GRAVY', 'AIOLI', 'KETCHUP'] },
    { label: 'Dances', items: ['TANGO', 'WALTZ', 'POLKA', 'SALSA'] }] },
  { groups: [
    { label: 'Weather', items: ['THUNDER', 'BREEZE', 'DRIZZLE', 'BLIZZARD'] },
    { label: 'Olympic sports', items: ['FENCING', 'ROWING', 'ARCHERY', 'JUDO'] },
    { label: 'Mythical creatures', items: ['DRAGON', 'PHOENIX', 'GRIFFIN', 'UNICORN'] },
    { label: 'National capitals', items: ['PARIS', 'CAIRO', 'LIMA', 'OSLO'] }] },
  { groups: [
    { label: 'Organs', items: ['HEART', 'LIVER', 'LUNG', 'KIDNEY'] },
    { label: 'Trees', items: ['OAK', 'MAPLE', 'BIRCH', 'WILLOW'] },
    { label: 'Board games', items: ['CHESS', 'GO', 'CHECKERS', 'BACKGAMMON'] },
    { label: 'Composers', items: ['BACH', 'CHOPIN', 'HANDEL', 'VIVALDI'] }] },
  { groups: [
    { label: 'Currencies', items: ['EURO', 'YEN', 'PESO', 'RUPEE'] },
    { label: 'Pasta', items: ['PENNE', 'FUSILLI', 'RAVIOLI', 'LINGUINE'] },
    { label: 'Noble gases', items: ['NEON', 'ARGON', 'XENON', 'HELIUM'] },
    { label: 'Painters', items: ['MONET', 'DALI', 'GOYA', 'KLIMT'] }] },
  { groups: [
    { label: 'Continents', items: ['ASIA', 'EUROPE', 'AFRICA', 'OCEANIA'] },
    { label: 'Card suits', items: ['HEARTS', 'CLUBS', 'SPADES', 'DIAMONDS'] },
    { label: 'Birds', items: ['ROBIN', 'HERON', 'FALCON', 'SPARROW'] },
    { label: 'Golf scores', items: ['BIRDIE', 'EAGLE', 'BOGEY', 'PAR'] }] },
  { groups: [
    { label: 'Desserts', items: ['TIRAMISU', 'BROWNIE', 'SORBET', 'CUSTARD'] },
    { label: 'Volcanoes', items: ['ETNA', 'FUJI', 'VESUVIUS', 'KRAKATOA'] },
    { label: 'Units of length', items: ['METRE', 'INCH', 'MILE', 'FURLONG'] },
    { label: 'Scientists', items: ['NEWTON', 'DARWIN', 'CURIE', 'TESLA'] }] },
  { groups: [
    { label: 'Fabrics', items: ['DENIM', 'SILK', 'VELVET', 'LINEN'] },
    { label: 'Sea creatures', items: ['SQUID', 'WALRUS', 'MANATEE', 'ORCA'] },
    { label: 'Martial arts', items: ['KARATE', 'JUDO', 'TAEKWONDO', 'KUNG FU'] },
    { label: 'Keyboard keys', items: ['SHIFT', 'ENTER', 'ESCAPE', 'DELETE'] }] },
  { groups: [
    { label: 'Cheeses', items: ['BRIE', 'GOUDA', 'FETA', 'CHEDDAR'] },
    { label: 'Islands', items: ['CUBA', 'BALI', 'MALTA', 'ICELAND'] },
    { label: 'Moons', items: ['TITAN', 'EUROPA', 'IO', 'PHOBOS'] },
    { label: 'Music markings', items: ['FORTE', 'PIANO', 'LARGO', 'ALLEGRO'] }] },
  { groups: [
    { label: 'Mountain ranges', items: ['ALPS', 'ANDES', 'ROCKIES', 'URALS'] },
    { label: 'Brass instruments', items: ['TRUMPET', 'TROMBONE', 'TUBA', 'HORN'] },
    { label: 'Dog breeds', items: ['POODLE', 'BOXER', 'HUSKY', 'DALMATIAN'] },
    { label: 'Famous ships', items: ['TITANIC', 'ENDEAVOUR', 'BEAGLE', 'MAYFLOWER'] }] },
  { groups: [
    { label: 'Shapes', items: ['CIRCLE', 'SQUARE', 'HEXAGON', 'RHOMBUS'] },
    { label: 'Spices', items: ['CUMIN', 'NUTMEG', 'PAPRIKA', 'SAFFRON'] },
    { label: 'Racket sports', items: ['TENNIS', 'SQUASH', 'BADMINTON', 'PADEL'] },
    { label: 'Constellations', items: ['ORION', 'LYRA', 'DRACO', 'PEGASUS'] }] },
];

/** Every Brain Teaser; each day takes the next one. */
export const BRAIN_TEASERS: BrainTeaser[] = [
  { question: 'What has keys but can\'t open locks?', options: ['A map', 'A piano', 'A prison', 'A car'], answer: 1,
    why: 'A piano has keys, but they play notes instead of opening anything.' },
  { question: 'The more you take, the more you leave behind. What are they?', options: ['Photos', 'Breaths', 'Footsteps', 'Coins'], answer: 2,
    why: 'Every step you take leaves another footprint behind you.' },
  { question: 'What gets wetter the more it dries?', options: ['A sponge', 'A towel', 'Rain', 'Soap'], answer: 1,
    why: 'A towel takes the water from whatever it dries.' },
  { question: 'A man has three daughters, and each daughter has one brother. How many children does he have?', options: ['3', '4', '6', '7'], answer: 1,
    why: 'All three daughters share the same brother: three girls and one boy.' },
  { question: 'What comes once in a minute, twice in a moment, but never in a thousand years?', options: ['The letter M', 'A second', 'A blink', 'The letter E'], answer: 0,
    why: 'Look at the spelling: "minute" has one M, "moment" has two, "a thousand years" has none.' },
  { question: 'How many months have 28 days?', options: ['1', '2', '6', 'All 12'], answer: 3,
    why: 'Every month has at least 28 days.' },
  { question: 'What can travel around the world while staying in a corner?', options: ['A compass', 'A stamp', 'A spider', 'A satellite'], answer: 1,
    why: 'A postage stamp stays in the corner of an envelope wherever the letter goes.' },
  { question: 'A bat and a ball cost $1.10 together. The bat costs $1.00 more than the ball. How much is the ball?', options: ['10 cents', '5 cents', '1 cent', '15 cents'], answer: 1,
    why: 'Ball 5 cents + bat $1.05 = $1.10, and $1.05 is $1.00 more than 5 cents. (10 cents would make the total $1.20.)' },
  { question: 'What has many teeth but can\'t bite?', options: ['A comb', 'A shark', 'A saw blade', 'A zip'], answer: 0,
    why: 'A comb\'s teeth only untangle hair.' },
  { question: 'I\'m tall when I\'m young and short when I\'m old. What am I?', options: ['A tree', 'A shadow', 'A candle', 'A pencil'], answer: 2,
    why: 'A candle burns down as it gets older.' },
  { question: 'If 5 machines take 5 minutes to make 5 widgets, how long do 100 machines take to make 100 widgets?', options: ['100 minutes', '20 minutes', '5 minutes', '1 minute'], answer: 2,
    why: 'Each machine makes one widget in 5 minutes, so 100 machines make 100 widgets in the same 5 minutes.' },
  { question: 'Which word becomes shorter when you add two letters to it?', options: ['Long', 'Brief', 'Short', 'Small'], answer: 2,
    why: 'Add "er" to "short" and you get "shorter".' },
  { question: 'What has one eye but can\'t see?', options: ['A hurricane', 'A needle', 'A potato', 'A cyclops'], answer: 1,
    why: 'A needle\'s eye is the hole the thread goes through.' },
  { question: 'A patch of lily pads doubles in size every day and covers the whole lake on day 48. On which day did it cover half the lake?', options: ['Day 24', 'Day 36', 'Day 46', 'Day 47'], answer: 3,
    why: 'It doubles each day, so it was half the size one day earlier.' },
  { question: 'What runs but never walks, and has a mouth but never talks?', options: ['A clock', 'A river', 'A tap', 'A road'], answer: 1,
    why: 'A river runs to the sea and ends at its mouth.' },
  { question: 'What is full of holes but still holds water?', options: ['A net', 'A sponge', 'A sieve', 'A cloud'], answer: 1,
    why: 'A sponge soaks water up into all its little holes.' },
  { question: 'What has hands but can\'t clap?', options: ['A clock', 'A statue', 'A glove', 'A robot'], answer: 0,
    why: 'A clock\'s hands point at the time.' },
  { question: 'What belongs to you, but other people use it more than you do?', options: ['Your phone', 'Your name', 'Your money', 'Your car'], answer: 1,
    why: 'Others say your name far more often than you do.' },
  { question: 'If you have me, you want to share me. If you share me, you haven\'t got me. What am I?', options: ['A secret', 'A joke', 'A cake', 'A gift'], answer: 0,
    why: 'Once a secret is shared, it isn\'t a secret any more.' },
  { question: 'What has a neck but no head?', options: ['A guitar', 'A bottle', 'A giraffe', 'A shirt'], answer: 1,
    why: 'A bottle has a neck at the top, but no head. (A guitar has both a neck and a head.)' },
];
