import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Analytics } from './core/analytics';
import { AuthService } from './core/auth.service';
import { GameFx } from './core/game-fx';
import { InstallPrompt } from './core/install-prompt';
import { World } from './core/world';
import { Aurora } from './motion/aurora';
import { ConsentBanner } from './ui/consent-banner';
import { MaintenanceBanner } from './ui/maintenance-banner';
import { Support } from './ui/support';

@Component({
  selector: 'app-root',
  imports: [NgTemplateOutlet, RouterOutlet, RouterLink, RouterLinkActive, Aurora, ConsentBanner, MaintenanceBanner, Support],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly fx = inject(GameFx);
  protected readonly installer = inject(InstallPrompt);
  protected readonly world = inject(World);

  constructor() {
    inject(Analytics).init();
    // heartbeat while signed in: puts the player on the "online now" map
    effect(() => (this.auth.isLoggedIn() ? this.world.start() : this.world.stop()));
  }
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/welcome');
  }
}
