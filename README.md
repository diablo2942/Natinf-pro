# NATINF Search Pro

Application Android **offline-first** avec mise à jour en 1 clic depuis **data.gouv.fr** et recherche « IA » (synonymes, normalisation, ranking).

## Fonctionnalités
- ✅ **Moteur de recherche** (mots-clés, numéros NATINF, articles), tolérance aux fautes/accents (normalisation, Jaro‑Winkler).
- ✅ **Synonymes FR** (assets/synonyms_fr.json) + classement « sémantique » Lite.
- ✅ **Favoris** (Room).
- ✅ **Partager en PDF** (FileProvider).
- ✅ **Liens Légifrance** (*recherche* sur legifrance.gouv.fr pour chaque article).
- ✅ **Mise à jour** depuis **data.gouv.fr** (API du jeu de données « Liste des infractions en vigueur de la nomenclature NATINF »).

## Données officielles
- Jeu de données : *Liste des infractions en vigueur de la nomenclature NATINF* — Ministère de la Justice (mise à jour trimestrielle, Licence Ouverte 2.0). Voir la page dataset sur data.gouv.fr.

## Importer la liste complète
1. Lancer l’application puis **bouton “Mettre à jour”** (icône refresh). L’app appelle l’API `https://www.data.gouv.fr/api/1/datasets/liste-des-infractions-en-vigueur-de-la-nomenclature-natinf/`, sélectionne la dernière ressource CSV et l’importe.
2. Si besoin, tu peux aussi remplacer `app/src/main/assets/natinf_sample.csv` par le CSV officiel avant build.

## Build
- Ouvrir dans **Android Studio** → *Build > Build APK(s)*.

## ONNX / embeddings (optionnel)
Un stub `OnnxSemanticEngine` est prêt pour brancher un modèle d’**embeddings** on‑device (ONNX). En attendant, le moteur **Lite** est utilisé. Pour activer ONNX :
- Ajouter un modèle dans `assets/onnx/model.onnx` et l’inférence (ONNX Runtime Mobile).
- Implémenter la conversion texte→embedding et le score cosinus.

---
