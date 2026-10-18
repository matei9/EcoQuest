export const scopeRefusal = (lang) => lang === 'en'
  ? 'I can help only with EcoQuest: quests, recycling, green transport, your eco impact, the tree, teams and 1v1 duels. Ask me about one of these.'
  : 'Te pot ajuta doar cu EcoQuest: misiuni, reciclare, transport verde, impactul tău eco, copacul, echipe și dueluri 1v1. Întreabă-mă despre unul dintre acestea.';

const normalize = (text) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
const topic = /\b(eco\s*quest|guardian\s*ai|aplicati\w*|app|quests?|misiun\w*|ecopoints?|ecocoins?|puncte|points?|coins?|quiz|trivia|streak|copac\w*|tree|garden|gradin\w*|duel\w*|1v1|echip\w*|teams?|prieteni?|friends?|clasament\w*|leaderboard\w*|ligi?|league\w*|profil\w*|profile|gps|camera|settings|setari|height|inaltime|cont\w*|account|monet|sgr|recicl\w*|recycl\w*|ambalaj\w*|packag\w*|bon\w*|receipt\w*|co2|carbon|eco|ecologi\w*|sustenab\w*|sustainab\w*|polu\w*|pollut\w*|gheata|ice|urs\w*|bears?|polar\w*|energie|energy|electric\w*|apa|water|risipa|waste|reutil\w*|reus\w*|carburant\w*|fuel|benzina|petrol|mers|walk\w*|bicicl\w*|cycl\w*|transport|autobuz\w*|bus|tramvai\w*|tram|piata|market|voucher\w*|recompens\w*|reward\w*)\b/;
const unrelatedTask = /\b(python|javascript|typescript|sql|html|css|programare|coding|cod sursa|reteta|recipe|horoscop|horoscope|alegeri|elections?|presedinte|president|fotbal|football|bitcoin|crypto|diagnostic|diagnosis|medicament\w*|medicin\w*)\b/;
const greeting = /^(salut|buna|hei|hello|hi|hey|multumesc|mersi|thanks|thank you|ok)[!. ?]*$/;
const followup = /^(de ce|cum asa|cum fac asta|cum incep|si apoi|si dupa|explica( mai mult)?|mai multe detalii|poti detalia|da-mi un exemplu|why|how so|how do i do that|how do i start|what next|explain( more)?|tell me more|give me an example)[!. ?]*$/;

/** Only a genuine short follow-up inherits topic; an earlier eco message cannot unlock unrelated tasks. */
export function inEcoScope(messages) {
  const users = messages.filter((m) => m.role === 'user').map((m) => normalize(m.content));
  const last = users.at(-1) ?? '';
  if (unrelatedTask.test(last)) return false;
  if (greeting.test(last) || topic.test(last)) return true;
  if (!followup.test(last)) return false;
  const previous = users.slice(0, -1).reverse().find((text) => !followup.test(text) && !greeting.test(text));
  return !!previous && !unrelatedTask.test(previous) && topic.test(previous);
}
