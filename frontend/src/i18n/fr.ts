export const navSections = {
  principal: 'Principal',
  supervision: 'Supervision',
  configuration: 'Configuration',
} as const

export const nav = {
  chat: 'Chat',
  dashboard: 'Tableau de bord',
  conversations: 'Historique',
  templates: 'Modèles prompts',
  models: 'Modèles IA',
  usage: 'Consommation',
  providers: 'Fournisseurs',
  routing: 'Routage',
  rateLimit: 'Limitation',
  observability: 'Observabilité',
  settings: 'Paramètres',
} as const

export const pages = {
  chat: { title: 'Chat', subtitle: 'Interface de conversation avec l\'IA.' },
  login: { title: 'Connexion', subtitle: 'Authentification Keycloak OIDC.' },
  profile: { title: 'Mon profil', subtitle: 'Compte et préférences.' },
  dashboard: { title: 'Tableau de bord', subtitle: 'Santé et perf du gateway.' },
  conversations: { title: 'Historique', subtitle: 'Conversations enregistrées.' },
  templates: { title: 'Modèles prompts', subtitle: 'Prompts réutilisables.' },
  models: { title: 'Modèles IA', subtitle: 'Activer, désactiver, modèle par défaut.' },
  usage: { title: 'Consommation', subtitle: 'Tokens, coût, latence.' },
  providers: { title: 'Fournisseurs', subtitle: 'Inférence cloud et local.' },
  routing: { title: 'Routage', subtitle: 'Routage par priorité.' },
  rateLimit: { title: 'Limitation', subtitle: 'Quotas par route et clé.' },
  observability: { title: 'Observabilité', subtitle: 'Métriques et alertes.' },
  settings: { title: 'Paramètres', subtitle: 'Auth et clés API.' },
} as const

export const fmt = {
  num: (n: number) => n.toLocaleString('fr-FR'),
  currency: (n: number) => {
    if (n === 0) return '0\u00a0$'
    if (n < 0.01) {
      return `${n.toLocaleString('fr-FR', { minimumFractionDigits: 4, maximumFractionDigits: 6 })}\u00a0$`
    }
    return `${n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}\u00a0$`
  },
}
