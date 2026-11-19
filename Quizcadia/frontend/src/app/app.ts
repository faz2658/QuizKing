import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { NavigationStart, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Analytics } from './core/analytics';
import { AuthService } from './core/auth.service';
import { GameFx } from './core/game-fx';
import { InstallPrompt } from './core/install-prompt';
import { World } from './core/world';
import { Aurora } from './motion/aurora';
import { NeuralBg } from './motion/neural-bg';
import { syncStill } from './motion/reduced-motion';
import { ConsentBanner } from './ui/consent-banner';
import { MaintenanceBanner } from './ui/maintenance-banner';
import { Support } from './ui/support';
import { ThemeSwitch } from './ui/theme-switch';

@Component({
  selector: 'app-root',
  imports: [NgTemplateOutlet, RouterOutlet, RouterLink, RouterLinkActive, Aurora, NeuralBg, ConsentBanner, MaintenanceBanner, Support, ThemeSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly fx = inject(GameFx);
  protected readonly installer = inject(InstallPrompt);
  protected readonly world = inject(World);

  private readonly router = inject(Router);

  constructor() {
    // no animations or transitions anywhere but the login page; set before the page (and the brain) react to it
    syncStill(location.pathname);
    this.router.events.subscribe((e) => e instanceof NavigationStart && syncStill(e.url));
    inject(Analytics).init();
    // heartbeat while signed in: puts the player on the "online now" map
    effect(() => (this.auth.isLoggedIn() ? this.world.start() : this.world.stop()));
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/welcome');
  }
}
