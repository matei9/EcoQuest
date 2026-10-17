# Deploy backend: Ubuntu 24.04 + port 5200 + Cloudflare (TLS la Cloudflare)

Arhitectura:

```
Telefon --HTTPS:443--> Cloudflare (TLS, proxy portocaliu) --HTTP:5200--> server Ubuntu (Node)
```

Pe server nu există nginx și nici certificat. Firewall-ul lasă portul 5200 deschis **doar pentru IP-urile Cloudflare**.

> **Atenție:** cu SSL „Flexible”, traficul Cloudflare → server circulă necriptat (HTTP). Pentru un hackathon e acceptabil. Pentru producție cu date reale ia în calcul Cloudflare Tunnel sau un certificat Origin pe server cu modul „Full (strict)”.

---

## 1. Pe server (Ubuntu 24.04)

Copiază proiectul pe server, de exemplu cu git sau scp:

```bash
scp -r GreenGuardians/server user@IP_SERVER:~/green-guardians-server
```

Conectează-te prin SSH și rulează instalarea:

```bash
ssh user@IP_SERVER
cd ~/green-guardians-server
sudo bash deploy/install-cloudflare.sh
```

Scriptul face următoarele:

- instalează Node.js 24;
- creează utilizatorul de sistem `greenguardians`;
- copiază codul în `/opt/green-guardians/server`;
- creează `/etc/green-guardians/api.env` cu `HOST=0.0.0.0`, `PORT=5200` și un `JWT_SECRET` aleator;
- pornește serviciul systemd `green-guardians-api`;
- **nu** modifică firewall-ul (îl gestionezi tu; recomandat: 5200 deschis doar pentru IP-urile Cloudflare);
- programează un backup zilnic al bazei SQLite.

La final trebuie să vezi `{"ok":true}`.

Opțional, adaugă cheia Tranzy pentru CTP Iași. Kilo AI nu are nevoie de cheie.

```bash
sudo nano /etc/green-guardians/api.env      # TRANZY_API_KEY=...
sudo systemctl restart green-guardians-api
```

## 2. În Cloudflare (dashboard)

1. **Domeniul în Cloudflare.** Adaugă domeniul (Add a site) și schimbă nameserverele la registrar cu cele date de Cloudflare. Aștepți până devine „Active”.
2. **DNS.** Mergi la **DNS → Records → Add record**:
   - Type: `A`
   - Name: `api` (rezultă `api.domeniul-tau.ro`)
   - IPv4: IP-ul serverului
   - Proxy status: **Proxied** (nor portocaliu)
3. **Portul 5200.** Cloudflare trimite implicit spre portul 80, iar 5200 nu e în lista lui de porturi. Mergi la **Rules → Origin Rules → Create rule**:
   - When: `Hostname` equals `api.domeniul-tau.ro`
   - Then: **Destination Port → Rewrite to** `5200`
   - Deploy.
4. **SSL.** Mergi la **SSL/TLS → Overview** și setează modul **Flexible**, pentru că serverul nu are TLS.
   - Dacă domeniul mai are alte site-uri care folosesc „Full”, lasă zona pe Full. Fă în schimb o regulă doar pentru API: **Rules → Configuration Rules** (Hostname equals `api.domeniul-tau.ro` → SSL: Flexible).
5. **Redirect la HTTPS.** Mergi la **SSL/TLS → Edge Certificates** și activează **Always Use HTTPS**.

## 3. Test

```bash
curl https://api.domeniul-tau.ro/api/health
```

Trebuie să răspundă `{"ok":true}`. Dacă ai restricționat 5200 la IP-urile Cloudflare, `http://IP_SERVER:5200` din afara Cloudflare nu trebuie să răspundă.

## 4. Aplicația Android

În `GreenGuardians/gradle.properties` adaugă:

```
GG_API_URL=https://api.domeniul-tau.ro
```

Apoi fă Sync și Run în Android Studio.

## Operare

| Ce | Comandă |
|---|---|
| Loguri live | `journalctl -u green-guardians-api -f` |
| Restart | `sudo systemctl restart green-guardians-api` |
| Actualizare cod | copiază noul `server/`, apoi `sudo bash deploy/install-cloudflare.sh` (păstrează DB + env) |
| Cod QR eveniment | `cd /opt/green-guardians/server && sudo -u greenguardians env $(sudo cat /etc/green-guardians/api.env \| xargs) npm run add-event -- COD "Curățenie Copou"` |
| Backup-uri | `/var/lib/green-guardians/backup-*.db` |
| Mod demo (juriu) | `DEMO_MODE=true` în `api.env`, apoi restart |

Lista IP-urilor Cloudflare (dacă vrei să restricționezi 5200 manual): https://www.cloudflare.com/ips-v4 și https://www.cloudflare.com/ips-v6.
