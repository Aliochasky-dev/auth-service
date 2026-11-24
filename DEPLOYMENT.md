# Guide de Déploiement - Auth Service (CampusConnect)

Ce microservice est prêt à être déployé. Voici les instructions pour le déployer sur AWS (ECS Fargate ou Kubernetes).

## 1. Variables d'environnement requises
Avant de déployer, vous devez configurer ces variables dans votre plateforme (AWS ECS, Kubernetes Secrets, ou Render) :

- `SPRING_DATASOURCE_URL` : jdbc:postgresql://campusconnect-postgres:5432/auth_db (À remplacer par l'URL RDS sur AWS)
- `SPRING_DATASOURCE_USERNAME` : postgres
- `SPRING_DATASOURCE_PASSWORD` : admin
- `JWT_SECRET` : super-secret-key-for-docker-production-1234567890
- `MAIL_USERNAME` : nzimbaaliocha15@gmail.com
- `MAIL_PASSWORD` : "mxxr oykv pfik mjex"
- `FACE_SERVICE_URL` : http://campusconnect-face-service:8000
- `BIOMETRIC_FP_DEV_ALLOW_UNSIGNED` : "false"
  
- Email : admin@campusconnect.cm
-Mot de passe : Admin123!
## 2. Déploiement rapide avec Docker (Pour tester en local)
Si vous avez Docker installé sur votre machine, tapez simplement :
```bash
docker compose up -d --build