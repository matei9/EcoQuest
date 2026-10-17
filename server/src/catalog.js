// Server-side catalog. Rewards here are AUTHORITATIVE; the Android copy
// (app/.../data/QuestRepository.kt) is only for display and must be kept in sync.

const FOOD = ['food', 'dish', 'meal', 'cuisine', 'vegetable', 'fruit', 'bread', 'soup', 'salad', 'pasta', 'rice', 'baked goods', 'recipe', 'cooking'];

// verification: metric (auto from trips/receipts/check-ins/completions) | confirm | timer | photo | ocr | bill
const Q = (id, kind, ep, co2, verification, extra = {}) => ({ id, kind, ep, co2, verification, coins: Math.floor(ep / 5), ...extra });
const M = (id, kind, ep, co2, metric, target) => Q(id, kind, ep, co2, 'metric', { metric, target });

export const quests = [
  M('d1', 'daily', 25, 0.2, 'walkKm', 1),
  M('d2', 'daily', 50, 0.6, 'walkKm', 3),
  M('d3', 'daily', 100, 2.0, 'cycleKm', 10),
  M('d4', 'daily', 25, 0.8, 'transitTrips', 1),
  M('d5', 'daily', 25, 0.15, 'sgr', 3),
  M('d6', 'daily', 50, 0.5, 'sgr', 10),
  M('d7', 'daily', 100, 1.0, 'sgr', 20),
  Q('d8', 'daily', 25, 0.1, 'confirm'),
  Q('d9', 'daily', 25, 0.45, 'timer'),
  Q('d10', 'daily', 25, 0.15, 'confirm'),
  Q('d11', 'daily', 25, 0.9, 'photo', { labels: FOOD }),
  Q('d12', 'daily', 50, 1.5, 'photo', { labels: FOOD }),
  Q('d13', 'daily', 25, 0.08, 'confirm'),
  M('d14', 'daily', 50, 0.75, 'transitKm', 5),
  Q('d15', 'daily', 100, 3.2, 'confirm'),

  M('w1a', 'weekly', 100, 2.0, 'walkKm', 10),
  M('w1b', 'weekly', 200, 5.0, 'walkKm', 25),
  M('w1c', 'weekly', 350, 10.0, 'walkKm', 50),
  M('w2a', 'weekly', 100, 2.4, 'transitTrips', 3),
  M('w2b', 'weekly', 200, 8.0, 'transitTrips', 10),
  M('w2c', 'weekly', 200, 5.0, 'transitDays', 5),
  M('w3a', 'weekly', 100, 1.0, 'sgr', 20),
  M('w3b', 'weekly', 200, 2.5, 'sgr', 50),
  M('w3c', 'weekly', 350, 5.0, 'sgr', 100),
  Q('w4', 'weekly', 100, 2.7, 'confirm'),
  Q('w5', 'weekly', 200, 4.5, 'photo', { labels: FOOD }),
  Q('w6', 'weekly', 100, 0.5, 'confirm'),
  M('w7a', 'weekly', 200, 6.0, 'cycleKm', 30),
  M('w7b', 'weekly', 350, 14.0, 'cycleKm', 70),
  M('w8a', 'weekly', 100, 0, 'dailyCount', 5),
  M('w8b', 'weekly', 350, 0, 'dailyCount', 12),
  M('w9', 'weekly', 200, 3.0, 'eventCount', 1),

  M('m1a', 'monthly', 300, 0, 'dailyCount', 20),
  M('m1b', 'monthly', 600, 0, 'dailyCount', 50),
  M('m1c', 'monthly', 1000, 0, 'dailyCount', 100),
  M('m2a', 'monthly', 300, 5.0, 'sgr', 100),
  M('m2b', 'monthly', 600, 12.5, 'sgr', 250),
  M('m2c', 'monthly', 1000, 25.0, 'sgr', 500),
  M('m3', 'monthly', 600, 10.0, 'walkKm', 50),
  M('m4', 'monthly', 1000, 20.0, 'greenKm', 100),
  M('m5', 'monthly', 600, 6.0, 'eventCount', 2),
  Q('m6', 'monthly', 600, 15.0, 'photo', { twoImages: true }),
  Q('m7', 'monthly', 300, 8.0, 'ocr'),
  Q('m8', 'monthly', 1000, 25.0, 'bill'),
  Q('m9', 'monthly', 600, 13.5, 'confirm'),
  M('m10', 'monthly', 1000, 0, 'weeklyCount', 4),
  M('m11', 'monthly', 600, 3.0, 'referrals', 3),

  M('chain1', 'daily', 25, 0.2, 'walkKm', 1),
  Q('chain2', 'daily', 25, 0.08, 'confirm', { requires: 'chain1' }),
  Q('chain3', 'daily', 50, 0.3, 'photo', { requires: 'chain2' }),
  Q('mystery', 'daily', 50, 0.5, 'photo'),
];

export const questById = new Map(quests.map((q) => [q.id, q]));

/** EcoBaseline: points per option, category per question. raw max 100 -> x25 EP. */
export const quiz = {
  points: [[15, 12, 7, 0], [15, 10, 5, 0], [10, 7, 3, 0], [10, 7, 3, 0], [5, 3, 0], [10, 7, 3, 0], [10, 6, 2, 0], [10, 7, 3, 0], [5, 3, 0], [10, 6, 2, 0]],
  category: [0, 0, 1, 1, 1, 2, 2, 3, 3, 4],
};

export function scoreQuiz(answers) {
  if (!Array.isArray(answers) || answers.length !== quiz.points.length) return null;
  const cats = [0, 0, 0, 0, 0];
  for (let i = 0; i < answers.length; i++) {
    const a = answers[i];
    if (!Number.isInteger(a) || a < 0 || a >= quiz.points[i].length) return null;
    cats[quiz.category[i]] += quiz.points[i][a];
  }
  return cats;
}

export const leagues = [
  ['Bronze', 0], ['Silver', 500], ['Gold', 1000], ['Platinum', 2000],
  ['Diamond', 3500], ['Master', 6000], ['Grandmaster', 10000], ['Eco Legend', 20000],
];

export function leagueRange(totalEP) {
  let i = leagues.length - 1;
  while (totalEP < leagues[i][1]) i--;
  return { name: leagues[i][0], min: leagues[i][1], max: i + 1 < leagues.length ? leagues[i + 1][1] : Number.MAX_SAFE_INTEGER };
}

export const CITIES = ['Iași', 'Suceava', 'Bacău', 'Altul'];
export const SCHOOLS = ['UAIC Iași', 'TUIASI (Politehnica Iași)', 'USV Suceava', 'Universitatea „Vasile Alecsandri” Bacău', 'Colegiul Național Iași'];

export const vouchers = [
  { id: 'coffee', cost: 150, title: '20% reducere cafea (recipient reutilizabil)', partner: 'Cafenea Specialty Iași' },
  { id: 'bike', cost: 300, title: 'Revizie gratuită bicicletă / voucher accesorii', partner: 'Bike Hub Moldova' },
  { id: 'zerowaste', cost: 250, title: 'Voucher 25 Lei produse Zero-Waste', partner: 'Magazin Eco Local' },
  { id: 'cinema', cost: 200, title: 'Bilet redus evenimente comunitare / cinema', partner: 'Partener Suceava & Bacău' },
];
