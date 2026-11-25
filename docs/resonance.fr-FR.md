# Focaliseur de résonance

Fabriquez un focaliseur sur un établi. Obtenir une émeraude débloque la recette dans les deux modes d’obtention des contrats. Le focaliseur figure également dans l’onglet créatif de Writ.

| Gauche | Centre | Droite |
| --- | --- | --- |
| Vide | Lingot de cuivre | Émeraude |
| Vide | Bâton | Lingot de cuivre |
| Cuir | Vide | Vide |

La première utilisation lie le focaliseur à votre personnage. Utilisez ensuite le focaliseur lié sur une créature pour lui accorder une exemption de contrat. Les porte-armures ne sont pas pris en charge. Les villageois exemptés refusent les nouveaux contrats et conservent leurs échanges existants.

Utilisez le focaliseur en vous accroupissant pour retirer une exemption que vous avez appliquée. Si vous perdez le focaliseur, liez-en un autre pour retirer cette exemption. Un opérateur peut lever toute exemption avec `/writ exemption clear <target>`.

Le délai par défaut est de 100 ticks (5 secondes). Pour le modifier, fermez le monde ou arrêtez le serveur, modifiez `cooldown_ticks` dans `<monde>/serverconfig/writ-resonance-server.toml`, puis redémarrez. Une valeur de 0 désactive le délai.
